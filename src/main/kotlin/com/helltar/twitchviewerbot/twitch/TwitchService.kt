package com.helltar.twitchviewerbot.twitch

import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.helix.domain.Stream
import com.helltar.twitchviewerbot.TwitchConfig
import com.helltar.twitchviewerbot.text.escapeHtml
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.util.*

class TwitchService(config: TwitchConfig) {

    private companion object {
        const val MAX_STREAMS_PER_REQUEST = 100
        const val THUMBNAIL_HEIGHT = 1080
        const val THUMBNAIL_WIDTH = 1920
        val log = KotlinLogging.logger {}
    }

    private val twitchClient =
        TwitchClientBuilder
            .builder()
            .withClientId(config.clientId)
            .withClientSecret(config.clientSecret)
            .withEnableHelix(true)
            .build()

    fun fetchActiveStreams(userLogins: List<String>): List<StreamInfo> {
        val uniqueLogins =
            userLogins
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }

        if (uniqueLogins.isEmpty())
            return emptyList()

        return try {
            uniqueLogins
                .chunked(MAX_STREAMS_PER_REQUEST)
                .flatMap { loginsChunk ->
                    twitchClient.helix
                        .getStreams(null, null, null, loginsChunk.size, null, null, null, loginsChunk)
                        .execute()
                        .streams
                        .map(::mapStream)
                }
        } catch (e: Exception) {
            log.error(e) { "failed to fetch active Twitch streams for ${uniqueLogins.size} channel(s)" }
            throw e
        }
    }

    private fun mapStream(stream: Stream): StreamInfo {
        val thumbnailUrl = stream.getThumbnailUrl(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT) + "?t=${System.currentTimeMillis()}"
        val uptime = stream.uptime.formatUptime()

        return StreamInfo(
            stream.userLogin,
            stream.userName.escapeHtml(),
            stream.title.escapeHtml(),
            stream.viewerCount,
            stream.gameName,
            thumbnailUrl,
            uptime
        )
    }

    private fun Duration.formatUptime() =
        String.format(Locale.ROOT, "%02d:%02d", toHours(), toMinutesPart())
}
