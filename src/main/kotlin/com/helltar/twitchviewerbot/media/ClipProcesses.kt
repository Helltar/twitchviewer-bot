package com.helltar.twitchviewerbot.media

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object ClipProcesses {

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

    fun ffmpegExtractAudio(inputFilename: String, outFilename: String, lengthTime: Long): Process {
        val command =
            listOf(
                "ffmpeg", "-i", inputFilename,
                "-t", "$lengthTime",
                "-vn", "-c:a", "copy",
                "-movflags", "+faststart",
                "-loglevel", "quiet", outFilename
            )

        return command.startProcessOrThrow("failed to start ffmpeg")
    }

    fun startStreamlinkProcess(channelName: String, outFilename: String, durationSec: Long, quality: String): Process {
        val command =
            listOf(
                "streamlink",
                "--stream-segmented-duration", "${durationSec}s", // streamlink stops on its own after this much media
                "https://www.twitch.tv/$channelName",
                quality,
                "-o", outFilename
            )

        return command.startProcessOrThrow("failed to start streamlink")
    }

    fun probeVideoInfo(inputFilename: String, timeout: Duration = 5.seconds): VideoInfo? {
        val command =
            listOf(
                "ffprobe",
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height:format=duration",
                "-of", "default=noprint_wrappers=1",
                inputFilename
            )

        val process =
            try {
                ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (e: Exception) {
                log.warn(e) { "failed to start ffprobe for $inputFilename" }
                return null
            }

        try {
            if (!process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)) {
                log.warn { "ffprobe didn't exit within $timeout for $inputFilename" }
                process.kill()
                return null
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            process.kill()
            throw e
        }

        if (process.exitValue() != 0) {
            log.warn { "ffprobe exited with code ${process.exitValue()} for $inputFilename" }
            return null
        }

        val values =
            process.inputStream.bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val separatorIndex = line.indexOf('=')

                    if (separatorIndex > 0)
                        line.substring(0, separatorIndex) to line.substring(separatorIndex + 1)
                    else
                        null
                }.toMap()
            }

        val width = values["width"]?.toIntOrNull()
        val height = values["height"]?.toIntOrNull()
        val durationSec = values["duration"]?.toDoubleOrNull()?.roundToInt()?.takeIf { it > 0 }

        return VideoInfo(width, height, durationSec)
            .takeIf { it.width != null || it.height != null || it.durationSec != null }
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
