package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Config.javaTempDir
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.twitch.BroadcastData
import com.helltar.twitchviewerbot.twitch.Twitch
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
        val log = KotlinLogging.logger {}
    }

    private val processes = ConcurrentLinkedQueue<Process>()

    override suspend fun run() {
        if (arguments.isEmpty()) {
            if (isUserListNotEmpty())
                fetchAndSendClips(loadUserChannels())
            else
                replyToMessage(localizedString(Strings.CLIP_COMMAND_INFO))
        } else {
            val arg = arguments.first().take(26)

            if (arg.endsWith(".") && arg.length > 1) {
                val prefix = arg.removeSuffix(".")
                val userChannels = loadUserChannels()
                val channels = userChannels.filter { it.startsWith(prefix, ignoreCase = true) }

                if (channels.isNotEmpty()) {
                    fetchAndSendClips(channels)
                    return
                }
            }

            if (checkChannelNameAndReplyIfInvalid(arg))
                clip(arg)
        }
    }

    suspend fun fetchAndSendClips(userLogins: List<String>) {
        Twitch.fetchActiveStreams(userLogins)?.let {
            if (it.isNotEmpty())
                retrieveAndSendClips(it)
            else
                replyToMessage(localizedString(Strings.EMPTY_ONLINE_LIST))
        }
            ?: replyToMessage(localizedString(Strings.TWITCH_EXCEPTION))
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
            log.error { "error processing clip for $channelLogin: ${e.message}" }
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
