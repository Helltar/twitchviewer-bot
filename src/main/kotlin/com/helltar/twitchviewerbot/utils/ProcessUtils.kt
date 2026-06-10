package com.helltar.twitchviewerbot.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object ProcessUtils {

    private val log = KotlinLogging.logger {}

    fun ffmpegPrepareClip(inputFilename: String, outFilename: String, lengthTime: Long): Process { // todo: needs review
        val command =
            listOf(
                "ffmpeg", "-i", inputFilename,
                "-fs", "9.9M", // if the file size exceeds 10MB, a black video thumbnail (preview) may appear on telegram
                "-t", "$lengthTime",
                "-c", "copy",
                "-loglevel", "quiet", outFilename
            )

        return command.startProcessOrThrow("failed to start ffmpeg")
    }

    fun startStreamlinkProcess(channelName: String, outFilename: String): Process {
        val command =
            listOf(
                "streamlink",
                "https://www.twitch.tv/$channelName",
                "720p,720p60,best",
                "-o", outFilename
            )

        return command.startProcessOrThrow("failed to start streamlink")
    }

    fun Process.kill(timeout: Duration = 5.seconds) {
        if (!isAlive) return

        val pid = pid()
        log.warn { "destroying process $pid" }
        destroy()

        runCatching {
            if (!waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)) {
                log.warn { "force destroying process $pid after timeout" }
                destroyForcibly().waitFor()
            }
        }.onFailure { e ->
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn { "interrupted while waiting process $pid to terminate, forcing destroy" }
            } else
                log.error(e) { "unexpected error killing process $pid" }

            destroyForcibly()
        }
    }

    private fun List<String>.startProcessOrThrow(errorMessage: String): Process =
        try {
            // nobody reads stdout/stderr, discard them so the pipe buffer can't fill up and block the process
            ProcessBuilder(this)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (e: Exception) {
            log.error(e) { "failed to start process: ${joinToString(" ")}" }
            throw RuntimeException(errorMessage, e)
        }
}
