package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Localization
import com.helltar.twitchviewerbot.bot.BotContext
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.coroutines.runCatchingPreservingCancellation
import com.helltar.twitchviewerbot.database.dao.usersDao
import com.helltar.twitchviewerbot.media.ClipFormat
import com.helltar.twitchviewerbot.media.ClipProcesses.ffmpegExtractAudio
import com.helltar.twitchviewerbot.media.ClipProcesses.ffmpegPrepareClip
import com.helltar.twitchviewerbot.media.ClipProcesses.kill
import com.helltar.twitchviewerbot.media.ClipProcesses.probeVideoInfo
import com.helltar.twitchviewerbot.media.ClipProcesses.startStreamlinkProcess
import com.helltar.twitchviewerbot.media.ClipTempStorage
import com.helltar.twitchviewerbot.media.VideoInfo
import com.helltar.twitchviewerbot.text.plusUUID
import com.helltar.twitchviewerbot.text.toTwitchHtmlLink
import com.helltar.twitchviewerbot.twitch.StreamInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class ClipCommand(
    botContext: BotContext<MessageContext>,
    private val format: ClipFormat = ClipFormat.VIDEO
) : TwitchCommand(botContext) {

    private companion object {
        const val MAX_CONCURRENT_CLIPS = 3
        const val DEFAULT_CLIP_DURATION_SEC = 30L

        // Bot API rejects uploads over 50 MB; decimal megabytes to stay under the limit regardless of how Telegram counts
        const val MAX_UPLOAD_SIZE_BYTES = 50_000_000L

        // stream copy keeps the source bitrate, so size scales ~linearly with duration;
        // the margin absorbs container overhead and the cut landing past the requested timestamp
        const val TRIM_SAFETY_FACTOR = 0.95

        // extra wall-clock headroom over the clip duration before the process is treated as hung and killed,
        // covering stream resolution, playlist fetching and initial buffering
        const val STREAMLINK_TIMEOUT_HEADROOM_SEC = 30L

        val CLIP_NAME_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
        val log = KotlinLogging.logger {}
    }

    private val processes = ConcurrentLinkedQueue<Process>()
    private var clipDurationSec = DEFAULT_CLIP_DURATION_SEC

    override suspend fun run() {
        if (arguments.isEmpty()) {
            val channels = loadUserChannels()

            if (channels.isNotEmpty())
                fetchAndSendClips(channels)
            else
                replyToMessage(localizedString(Localization.CLIP_COMMAND_INFO))

            return
        }

        val input = arguments.first().take(27)

        if (input.endsWith(".") && input.length > 1) {
            val isExclude = input.startsWith("!") && input.length > 2

            val prefix =
                if (!isExclude)
                    input.removeSuffix(".")
                else
                    input.removePrefix("!").removeSuffix(".")

            val userChannels = loadUserChannels()

            val filtered =
                if (!isExclude)
                    userChannels.filter { it.startsWith(prefix, ignoreCase = true) }
                else
                    userChannels.filterNot { it.startsWith(prefix, ignoreCase = true) }

            if (filtered.isNotEmpty())
                fetchAndSendClips(filtered)
            else
                replyToMessage(localizedString(Localization.FILTER_NO_CHANNELS_FOUND).format(input))

            return
        }

        if (checkChannelNameAndReplyIfInvalid(input))
            clip(input)
    }

    suspend fun fetchAndSendClips(userLogins: List<String>) {
        clipDurationSec = usersDao.clipDuration(userId).toLong()

        val activeStreams =
            runCatchingPreservingCancellation { twitchService.fetchActiveStreams(userLogins) }
                .getOrElse {
                    replyToMessage(localizedString(Localization.TWITCH_EXCEPTION))
                    return
                }

        if (activeStreams.isNotEmpty())
            retrieveAndSendClips(activeStreams)
        else
            replyToMessage(localizedString(Localization.EMPTY_ONLINE_LIST))
    }

    suspend fun clip(channel: String) =
        fetchAndSendClips(listOf(channel))

    private suspend fun retrieveAndSendClips(streams: List<StreamInfo>) = coroutineScope {
        streams.chunked(MAX_CONCURRENT_CLIPS).forEach { chunk ->
            ensureActive()
            processClipBatch(chunk)
        }
    }

    private suspend fun processClipBatch(chunk: List<StreamInfo>) = coroutineScope {
        val channelLinks = chunk.joinToString { it.login.toTwitchHtmlLink(it.username) }
        val statusMessageId = replyToMessage(localizedString(startMessageKey()).format(channelLinks, clipDurationSec))

        val jobs =
            chunk.map { stream ->
                launch {
                    downloadAndSendClip(stream)
                }
            }

        try {
            jobs.joinAll()
        } finally {
            if (processes.isNotEmpty()) {
                log.warn { "stopping ${processes.size} leftover process(es) for user-$userId: pids ${processes.map { it.pid() }}" }
                processes.forEach { it.kill() }
                processes.clear()
            }
            deleteMessageAsync(statusMessageId)
        }
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

            val videoInfo = prepareVideoInfo(outFile)
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
                // remove before kill() so the leftover cleanup in processClipBatch can't race and kill it twice
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

    private fun generateOutputFilename(prefix: String, tempName: String, extension: String) =
        ClipTempStorage.clipsDir.resolve("${prefix}_$tempName.$extension").toString()

    private fun clipDisplayName(channelLogin: String): String =
        "${channelLogin}_${LocalDateTime.now().format(CLIP_NAME_TIMESTAMP)}.${format.fileExtension}"
}
