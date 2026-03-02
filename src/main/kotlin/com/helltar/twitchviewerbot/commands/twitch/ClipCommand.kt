package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.twitch.BroadcastData
import com.helltar.twitchviewerbot.utils.ProcessUtils.ffmpegPrepareClip
import com.helltar.twitchviewerbot.utils.ProcessUtils.kill
import com.helltar.twitchviewerbot.utils.ProcessUtils.startStreamlinkProcess
import com.helltar.twitchviewerbot.utils.StringUtils.plusUUID
import com.helltar.twitchviewerbot.utils.StringUtils.toTwitchHtmlLink
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class ClipCommand(ctx: MessageContext) : TwitchCommand(ctx) {

    private companion object {
        const val MAX_SIMULTANEOUS_CLIP_DOWNLOADS = 3
        const val MAX_STREAMLINK_CLIP_DURATION_SEC = 40L
        const val FFMPEG_PROCESS_TIMEOUT = MAX_STREAMLINK_CLIP_DURATION_SEC
        val javaTempDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        val log = KotlinLogging.logger {}
    }

    private val processes = ConcurrentLinkedQueue<Process>()

    override suspend fun run() {
        if (arguments.isEmpty()) {
            if (isUserListNotEmpty())
                fetchAndSendClips(loadUserChannels())
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
        val activeStreams =
            runCatching { twitchService.fetchActiveStreams(userLogins) }
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

    private suspend fun retrieveAndSendClips(twitchBroadcastData: List<BroadcastData>) = coroutineScope {
        twitchBroadcastData.chunked(MAX_SIMULTANEOUS_CLIP_DOWNLOADS).forEach { chunk ->
            ensureActive()
            processClipBatch(chunk)
        }
    }

    private suspend fun processClipBatch(chunk: List<BroadcastData>) = coroutineScope {
        val localizedMessage = localizedString(Strings.START_GET_CLIP)
        val chunkHtmlLinks = chunk.joinToString { it.login.toTwitchHtmlLink(it.username) }
        val tempMessage = localizedMessage.format(chunkHtmlLinks)
        val tempMessageId = replyToMessage(tempMessage)

        val jobs =
            chunk.map { broadcastData ->
                launch {
                    downloadAndSendClip(broadcastData)
                }
            }

        try {
            jobs.joinAll()
        } catch (e: CancellationException) {
            log.warn { "cancel all user-$userId jobs (${jobs.size}) and destroy processes (${processes.size}): ${e.message}" }
            processes.forEach { it.kill() }
        } finally {
            processes.clear()
            deleteMessageAsync(tempMessageId)
        }
    }

    private suspend fun downloadAndSendClip(broadcastData: BroadcastData) {
        val channelLogin = broadcastData.login
        val tempName = channelLogin.plusUUID()
        val streamlinkOutFilename = generateOutputFilename("streamlink", tempName)
        val ffmpegOutFilename = generateOutputFilename("ffmpeg", tempName)

        try {
            ensureActive {
                startStreamlinkProcess(channelLogin, streamlinkOutFilename)
                    .wait(MAX_STREAMLINK_CLIP_DURATION_SEC)
            }

            ensureActive {
                ffmpegPrepareClip(streamlinkOutFilename, ffmpegOutFilename, MAX_STREAMLINK_CLIP_DURATION_SEC)
                    .wait(FFMPEG_PROCESS_TIMEOUT)
            }

            if (File(ffmpegOutFilename).exists())
                replyToMessageWithVideo(ffmpegOutFilename, createHtmlCaption(broadcastData))
            else
                replyToMessage(localizedString(Strings.GET_CLIP_FAIL))
        } catch (e: Exception) {
            log.warn { "error processing clip for $channelLogin: ${e.message}" }
        } finally {
            File(ffmpegOutFilename).delete()
            File(streamlinkOutFilename).delete()
        }
    }

    private suspend inline fun ensureActive(block: () -> Unit) {
        block()
        currentCoroutineContext().ensureActive()
    }

    private fun Process.wait(timeout: Long) {
        processes.add(this)

        try {
            if (!this.waitFor(timeout, TimeUnit.SECONDS))
                this.destroy()
        } finally {
            processes.remove(this)
        }
    }

    private fun generateOutputFilename(prefix: String, tempName: String) =
        "$javaTempDir/${prefix}_$tempName.mp4"
}
