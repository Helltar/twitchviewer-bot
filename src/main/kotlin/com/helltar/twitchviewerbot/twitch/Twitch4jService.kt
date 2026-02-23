package com.helltar.twitchviewerbot.twitch

import com.github.twitch4j.TwitchClientBuilder
import com.helltar.twitchviewerbot.Config.twitchClientId
import com.helltar.twitchviewerbot.Config.twitchClientSecret
import io.github.oshai.kotlinlogging.KotlinLogging

object Twitch4jService : TwitchService {

    private val twitchClient =
        TwitchClientBuilder
            .builder()
            .withClientId(twitchClientId)
            .withClientSecret(twitchClientSecret)
            .withEnableHelix(true)
            .withFeignLogLevel(feign.Logger.Level.FULL)
            .build()

    private val log = KotlinLogging.logger {}

    override fun fetchActiveStreams(userLogins: List<String>): List<BroadcastData> {
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
