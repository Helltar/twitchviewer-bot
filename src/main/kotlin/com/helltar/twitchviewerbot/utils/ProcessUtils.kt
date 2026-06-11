package com.helltar.twitchviewerbot.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object ProcessUtils {

    private val log = KotlinLogging.logger {}

    fun ffmpegPrepareClip(inputFilename: String, outFilename: String, lengthTime: Long): Process {
        val command =
            listOf(
                "ffmpeg", "-i", inputFilename,
                "-t", "$lengthTime",
                "-c", "copy",
                // streamlink writes raw MPEG-TS; remux into MP4 with moov atom at the front so Telegram
                // reads duration/dimensions and shows an inline player instead of a black "document" preview
                "-movflags", "+faststart",
                "-loglevel", "quiet", outFilename
            )

        return command.startProcessOrThrow("failed to start ffmpeg")
    }

    fun startStreamlinkProcess(channelName: String, outFilename: String, durationSec: Long): Process {
        val command =
            listOf(
                "streamlink",
                "--stream-segmented-duration", "${durationSec}s", // streamlink stops on its own after this much media
                "https://www.twitch.tv/$channelName",
                "720p,720p60,best",
                "-o", outFilename
            )

        return command.startProcessOrThrow("failed to start streamlink")
    }

    fun Process.kill(timeout: Duration = 5.seconds) {
        if (!isAlive) return

        val description = "${commandName()} (pid ${pid()})"
        log.info { "stopping $description" }
        destroy()

        runCatching {
            if (!waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)) {
                log.warn { "$description didn't exit within $timeout, destroying forcibly" }
                destroyForcibly().waitFor()
            }
        }.onFailure { e ->
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn { "interrupted while waiting for $description to exit, destroying forcibly" }
            } else
                log.error(e) { "unexpected error stopping $description" }

            destroyForcibly()
        }
    }

    private fun Process.commandName(): String =
        info().command().getOrNull()?.substringAfterLast('/') ?: "process"

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
