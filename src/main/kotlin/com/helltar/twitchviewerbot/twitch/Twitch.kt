package com.helltar.twitchviewerbot.twitch

import com.github.twitch4j.TwitchClientBuilder
import com.helltar.twitchviewerbot.Config.twitchClientId
import com.helltar.twitchviewerbot.Config.twitchClientSecret
import com.helltar.twitchviewerbot.utils.StringUtils.escapeHtml
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Twitch {

    private val twitchClient =
        TwitchClientBuilder
            .builder()
            .withClientId(twitchClientId)
            .withClientSecret(twitchClientSecret)
            .withEnableHelix(true)
            .build()

    private val log = KotlinLogging.logger {}

    fun fetchActiveStreams(userLogins: List<String>): List<BroadcastData>? = try {
        twitchClient.helix.getStreams(null, null, null, 1, null, null, null, userLogins).execute().streams
            .map {
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val startedAt = it.startedAtInstant.atZone(ZoneId.systemDefault()).format(formatter)
                val thumbnailUrl = it.getThumbnailUrl(1920, 1080) + "?t=${System.currentTimeMillis()}"
                val uptime = LocalTime.MIN.plus(it.uptime).format(formatter)

                BroadcastData(
                    it.userLogin, it.userName.escapeHtml(), it.title.escapeHtml(),
                    it.viewerCount, it.gameName, thumbnailUrl, startedAt, uptime
                )
            }
    } catch (e: Exception) {
        log.error { e.message }
        null
    }
}
