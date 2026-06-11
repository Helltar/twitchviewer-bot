package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.bot.BotContext
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.database.dao.usersDao
import com.helltar.twitchviewerbot.twitch.StreamInfo
import com.helltar.twitchviewerbot.utils.ProcessUtils.ffmpegExtractAudio
import com.helltar.twitchviewerbot.utils.ProcessUtils.ffmpegPrepareClip
import com.helltar.twitchviewerbot.utils.ProcessUtils.kill
import com.helltar.twitchviewerbot.utils.ProcessUtils.startStreamlinkProcess
import com.helltar.twitchviewerbot.utils.StringUtils.plusUUID
import com.helltar.twitchviewerbot.utils.StringUtils.toTwitchHtmlLink
import com.helltar.twitchviewerbot.utils.TempStorage
import com.helltar.twitchviewerbot.utils.runCatchingPreservingCancellation
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
                replyToMessage(localizedString(Strings.CLIP_COMMAND_INFO))

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
                replyToMessage(localizedString(Strings.FILTER_NO_CHANNELS_FOUND).format(input))

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
                    replyToMessage(localizedString(Strings.TWITCH_EXCEPTION))
                    return
                }

        if (activeStreams.isNotEmpty())
            retrieveAndSendClips(activeStreams)
        else
            replyToMessage(localizedString(Strings.EMPTY_ONLINE_LIST))
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
        val statusMessageId = replyToMessage(localizedString(Strings.START_GET_CLIP).format(channelLinks, clipDurationSec))

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

            prepareMedia(streamlinkFile, outFile)
                .waitForExit(clipDurationSec)

            currentCoroutineContext().ensureActive()

            if (File(outFile).exists())
                sendMedia(outFile, stream)
            else
                replyToMessage(localizedString(Strings.GET_CLIP_FAIL))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn { "error processing clip for $channelLogin: ${e.message}" }
        } finally {
            File(outFile).delete()
            File(streamlinkFile).delete()
        }
    }

    private fun prepareMedia(inputFile: String, outFile: String): Process =
        when (format) {
            ClipFormat.VIDEO -> ffmpegPrepareClip(inputFile, outFile, clipDurationSec)
            ClipFormat.AUDIO -> ffmpegExtractAudio(inputFile, outFile, clipDurationSec)
        }

    private fun sendMedia(file: String, stream: StreamInfo) {
        val displayName = clipDisplayName(stream.login)

        when (format) {
            ClipFormat.VIDEO -> replyToMessageWithVideo(file, displayName, createHtmlCaption(stream))
            ClipFormat.AUDIO ->
                replyToMessageWithAudio(file, displayName, stream.login, createHtmlCaption(stream), clipDurationSec.toInt())
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
        TempStorage.clipsDir.resolve("${prefix}_$tempName.$extension").toString()

    private fun clipDisplayName(channelLogin: String): String =
        "${channelLogin}_${LocalDateTime.now().format(CLIP_NAME_TIMESTAMP)}.${format.fileExtension}"
}
