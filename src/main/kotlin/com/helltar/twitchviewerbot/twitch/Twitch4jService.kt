package com.helltar.twitchviewerbot.twitch

import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.helix.domain.Stream
import com.helltar.twitchviewerbot.TwitchConfig
import com.helltar.twitchviewerbot.utils.StringUtils.escapeHtml
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class Twitch4jService(config: TwitchConfig) {

    private companion object {
        const val MAX_STREAMS_PER_REQUEST = 100
        const val THUMBNAIL_HEIGHT = 1080
        const val THUMBNAIL_WIDTH = 1920
        val systemZoneId: ZoneId = ZoneId.systemDefault()
        val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val log = KotlinLogging.logger {}
    }

    private val twitchClient =
        TwitchClientBuilder
            .builder()
            .withClientId(config.clientId)
            .withClientSecret(config.clientSecret)
            .withEnableHelix(true)
            .build()

    fun fetchActiveStreams(userLogins: List<String>): List<BroadcastData> {
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

    private fun mapStream(stream: Stream): BroadcastData {
        val startedAt = stream.startedAtInstant.atZone(systemZoneId).format(timeFormatter)
        val thumbnailUrl = stream.getThumbnailUrl(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT) + "?t=${System.currentTimeMillis()}"
        val uptime = stream.uptime.formatUptime()

        return BroadcastData(
            stream.userLogin,
            stream.userName.escapeHtml(),
            stream.title.escapeHtml(),
            stream.viewerCount,
            stream.gameName,
            thumbnailUrl,
            startedAt,
            uptime
        )
    }

    private fun Duration.formatUptime() =
        String.format(Locale.ROOT, "%02d:%02d", toHours(), toMinutesPart())
}
