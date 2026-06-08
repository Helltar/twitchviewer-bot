package com.helltar.twitchviewerbot.twitch

import com.github.twitch4j.TwitchClientBuilder
import com.helltar.twitchviewerbot.TwitchConfig
import io.github.oshai.kotlinlogging.KotlinLogging

class Twitch4jService(config: TwitchConfig) {

    private companion object {
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
        if (userLogins.isEmpty())
            return emptyList()

        return try {
            userLogins
                .chunked(100)
                .flatMap { loginsChunk ->
                    twitchClient.helix
                        .getStreams(null, null, null, loginsChunk.size, null, null, null, loginsChunk)
                        .execute()
                        .streams
                        .map(StreamToBroadcastDataMapper::map)
                }
        } catch (e: Exception) {
            log.error(e) { "failed to fetch active Twitch streams for ${userLogins.size} channel(s)" }
            throw e
        }
    }
}
