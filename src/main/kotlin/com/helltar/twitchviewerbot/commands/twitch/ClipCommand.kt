package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Localization
import com.helltar.twitchviewerbot.bot.BotContext
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.database.dao.usersDao
import com.helltar.twitchviewerbot.media.ClipFormat
import com.helltar.twitchviewerbot.media.ClipProcesses.ffmpegExtractAudio
import com.helltar.twitchviewerbot.media.ClipProcesses.ffmpegPrepareClip
import com.helltar.twitchviewerbot.media.ClipProcesses.kill
import com.helltar.twitchviewerbot.media.ClipProcesses.probeVideoInfo
import com.helltar.twitchviewerbot.media.ClipProcesses.startStreamlinkProcess
import com.helltar.twitchviewerbot.media.ClipSlots
import com.helltar.twitchviewerbot.media.ClipTempStorage
import com.helltar.twitchviewerbot.media.VideoInfo
import com.helltar.twitchviewerbot.text.plusUUID
import com.helltar.twitchviewerbot.text.toTwitchHtmlLink
import com.helltar.twitchviewerbot.twitch.StreamInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class ClipCommand(
    botContext: BotContext<MessageContext>,
    private val format: ClipFormat = ClipFormat.VIDEO
) : TwitchCommand(botContext) {

    private companion object {
        const val DEFAULT_CLIP_DURATION_SEC = 30L

        // Bot API rejects uploads over 50 MB; decimal megabytes to stay under the limit regardless of how Telegram counts
        const val MAX_UPLOAD_SIZE_BYTES = 50_000_000L

        // stream copy keeps the source bitrate, so size scales ~linearly with duration;
        // the margin absorbs container overhead and the cut landing past the requested timestamp
        const val TRIM_SAFETY_FACTOR = 0.95

        // extra wall-clock headroom over the clip duration before the process is treated as hung and killed,
        // covering stream resolution, playlist fetching and initial buffering
        const val STREAMLINK_TIMEOUT_HEADROOM_SEC = 30L

        // clips that start together, as when a batch finishes and frees several slots, share one status edit
        val STATUS_EDIT_INTERVAL = 1.seconds

        val CLIP_NAME_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
        val log = KotlinLogging.logger {}
    }

    private val processes = ConcurrentLinkedQueue<Process>()
    private var clipDurationSec = DEFAULT_CLIP_DURATION_SEC

    override suspend fun run() {
        val selection = selectChannels(Localization.CLIP_COMMAND_INFO) ?: return
        fetchAndSendClips(selection.channels, selection.named)
    }

    suspend fun fetchAndSendClips(channels: List<String>, named: Set<String> = emptySet()) {
        clipDurationSec = usersDao.clipDuration(userId).toLong()

        val streams = fetchLiveStreams(channels, named)

        if (streams.isEmpty())
            return

        val slots = ClipSlots.forUser(userId)

        val tickets =
            slots.claim(streams.map { slotKey(it) })
                ?: run {
                    replyToMessage(localizedString(Localization.CLIP_QUEUE_FULL).format(ClipSlots.MAX_PENDING))
                    return
                }

        val clips = streams.mapNotNull { stream -> tickets[slotKey(stream)]?.let { stream to it } }

        if (clips.isEmpty()) {
            replyToMessage(localizedString(Localization.CLIP_ALREADY_QUEUED).format(channelLinks(streams)))
            return
        }

        try {
            recordClips(clips)
        } finally {
            // a clip cancelled before its coroutine ever ran never reached its own close
            tickets.values.forEach { it.close() }
        }
    }

    suspend fun clip(channel: String) =
        fetchAndSendClips(listOf(channel), setOf(channel))

    private suspend fun recordClips(clips: List<Pair<StreamInfo, ClipSlots.Ticket>>) = coroutineScope {
        // the clips that find a free slot start right away, the rest wait behind this or another request of the user
        val (recording, waiting) = clips.partition { (_, ticket) -> ticket.tryTakeSlot() }
        val status = ClipStatus(recording.map { it.first }, waiting.map { it.first })
        val statusText = status.text.value
        val statusMessageId = replyToMessage(statusText)
        val statusUpdates = launch { showStatusUpdates(statusMessageId, statusText, status) }

        val jobs =
            clips.map { (stream, ticket) ->
                launch {
                    try {
                        if (!ticket.hasSlot) {
                            ticket.awaitSlot()
                            status.started(stream)
                        }

                        downloadAndSendClip(stream)
                    } finally {
                        status.finished(stream)
                        ticket.close()
                    }
                }
            }

        try {
            jobs.joinAll()
        } finally {
            statusUpdates.cancel()

            if (processes.isNotEmpty()) {
                log.warn { "stopping ${processes.size} leftover process(es) for user-$userId: pids ${processes.map { it.pid() }}" }
                processes.forEach { it.kill() }
                processes.clear()
            }

            deleteMessageAsync(statusMessageId)
        }
    }

    private suspend fun showStatusUpdates(messageId: Int, initialText: String, status: ClipStatus) {
        var shown = initialText

        status.text.collect { text ->
            if (text != shown) {
                editMessage(messageId, text)
                shown = text
                delay(STATUS_EDIT_INTERVAL)
            }
        }
    }

    /** What the status message of one request shows: its clips recording now and those waiting for a slot. */
    private inner class ClipStatus(recording: List<StreamInfo>, waiting: List<StreamInfo>) {

        private val lock = Any()
        private val recording = recording.toMutableList() // guarded by lock
        private val waiting = waiting.toMutableList() // guarded by lock

        val text = MutableStateFlow(render())

        fun started(stream: StreamInfo) {
            synchronized(lock) {
                waiting.remove(stream)
                recording.add(stream)
                text.value = render()
            }
        }

        // not shown on its own: the finished clip is already in the chat, and the next one to start redraws the message
        fun finished(stream: StreamInfo) {
            synchronized(lock) {
                recording.remove(stream)
            }
        }

        private fun render(): String =
            buildList {
                if (recording.isNotEmpty())
                    add(localizedString(startMessageKey()).format(channelLinks(recording), clipDurationSec))

                if (waiting.isNotEmpty())
                    add(localizedString(Localization.CLIP_QUEUED).format(channelLinks(waiting)))
            }.joinToString("\n")
    }

    private suspend fun downloadAndSendClip(stream: StreamInfo) {
        val channelLogin = stream.login
        val tempName = channelLogin.plusUUID()
        val streamlinkFile = generateOutputFilename("streamlink", tempName, "ts")
        val outFile = generateOutputFilename("out", tempName, format.fileExtension)

        try {
            log.info { "recording ${clipDurationSec}s ${format.name.lowercase()} clip of $channelLogin for user-$userId" }

            startStreamlinkProcess(channelLogin, streamlinkFile, clipDurationSec, format.streamlinkQuality)
                .waitForExit(clipDurationSec + STREAMLINK_TIMEOUT_HEADROOM_SEC)

            currentCoroutineContext().ensureActive()

            prepareMedia(streamlinkFile, outFile, clipDurationSec)
                .waitForExit(clipDurationSec)

            currentCoroutineContext().ensureActive()

            val clipFile = File(outFile)

            if (!clipFile.exists()) {
                replyToMessage(localizedString(Localization.GET_CLIP_FAIL))
                return
            }

            val trimmedDurationSec =
                if (clipFile.length() > MAX_UPLOAD_SIZE_BYTES)
                    trimToUploadLimit(streamlinkFile, outFile, clipFile.length())
                else
                    null

            currentCoroutineContext().ensureActive()

            if (!clipFile.exists() || clipFile.length() > MAX_UPLOAD_SIZE_BYTES) {
                log.warn { "clip of $channelLogin for user-$userId exceeds the upload limit even after trimming" }
                replyToMessage(localizedString(Localization.CLIP_TOO_LARGE))
                return
            }

            val videoInfo = if (format == ClipFormat.VIDEO) prepareVideoInfo(outFile) else null
            sendMedia(outFile, stream, videoInfo, trimmedDurationSec)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn { "error processing clip for $channelLogin: ${e.message}" }
        } finally {
            File(outFile).delete()
            File(streamlinkFile).delete()
        }
    }

    private fun prepareMedia(inputFile: String, outFile: String, durationSec: Long): Process =
        when (format) {
            ClipFormat.VIDEO -> ffmpegPrepareClip(inputFile, outFile, durationSec)
            ClipFormat.AUDIO -> ffmpegExtractAudio(inputFile, outFile, durationSec)
        }

    private suspend fun trimToUploadLimit(streamlinkFile: String, outFile: String, clipSizeBytes: Long): Long {
        // the recording can come out shorter than requested (stream lag, late start), so size the cut off the real duration
        val actualDurationSec = prepareVideoInfo(outFile)?.durationSec?.toLong() ?: clipDurationSec

        val trimmedDurationSec =
            (actualDurationSec * TRIM_SAFETY_FACTOR * MAX_UPLOAD_SIZE_BYTES / clipSizeBytes).toLong().coerceAtLeast(1)

        log.info { "clip is $clipSizeBytes bytes, re-cutting from $actualDurationSec to $trimmedDurationSec s to fit the upload limit" }

        File(outFile).delete()

        prepareMedia(streamlinkFile, outFile, trimmedDurationSec)
            .waitForExit(clipDurationSec)

        return trimmedDurationSec
    }

    private suspend fun prepareVideoInfo(videoFile: String): VideoInfo? =
        runInterruptible(Dispatchers.IO) { probeVideoInfo(videoFile) }

    private fun startMessageKey(): String =
        when (format) {
            ClipFormat.VIDEO -> Localization.START_GET_CLIP
            ClipFormat.AUDIO -> Localization.START_GET_AUDIO_CLIP
        }

    private fun sendMedia(file: String, stream: StreamInfo, videoInfo: VideoInfo?, trimmedDurationSec: Long? = null) {
        val displayName = clipDisplayName(stream.login)

        var caption = createHtmlCaption(stream)

        if (trimmedDurationSec != null)
            caption += "\n\n" + localizedString(Localization.CLIP_TRIMMED).format(trimmedDurationSec)

        when (format) {
            ClipFormat.VIDEO -> replyToMessageWithVideo(file, displayName, caption, videoInfo)
            ClipFormat.AUDIO ->
                replyToMessageWithAudio(file, displayName, stream.login, caption, (trimmedDurationSec ?: clipDurationSec).toInt())
        }
    }

    private suspend fun Process.waitForExit(timeout: Long) {
        processes.add(this)

        try {
            if (!runInterruptible(Dispatchers.IO) { waitFor(timeout, TimeUnit.SECONDS) }) {
                // remove before kill() so the leftover cleanup in recordClips can't race and kill it twice
                processes.remove(this)
                kill()
            }
        } catch (e: CancellationException) {
            processes.remove(this)
            kill()
            throw e
        } finally {
            processes.remove(this)
        }
    }

    // video and audio of one channel are separate clips, both may be queued at once
    private fun slotKey(stream: StreamInfo) =
        "${format.name.lowercase()}:${stream.login.lowercase()}"

    private fun channelLinks(streams: List<StreamInfo>) =
        streams.joinToString { it.login.toTwitchHtmlLink(it.username) }

    private fun generateOutputFilename(prefix: String, tempName: String, extension: String) =
        ClipTempStorage.clipsDir.resolve("${prefix}_$tempName.$extension").toString()

    private fun clipDisplayName(channelLogin: String): String =
        "${channelLogin}_${LocalDateTime.now().format(CLIP_NAME_TIMESTAMP)}.${format.fileExtension}"
}
