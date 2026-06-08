package com.helltar.twitchviewerbot.twitch

import com.github.twitch4j.helix.domain.Stream
import com.helltar.twitchviewerbot.utils.StringUtils.escapeHtml
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object StreamToBroadcastDataMapper {

    private const val THUMBNAIL_WIDTH = 1920
    private const val THUMBNAIL_HEIGHT = 1080
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val systemZoneId = ZoneId.systemDefault()

    fun map(stream: Stream): BroadcastData {
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
