package com.helltar.twitchviewerbot.bot

import com.helltar.twitchviewerbot.TelegramConfig
import com.helltar.twitchviewerbot.twitch.Twitch4jService

data class BotSettings(
    val creatorId: Long,
    val username: String
)

data class BotDependencies(
    val settings: BotSettings,
    val twitchService: Twitch4jService
)

data class BotContext<T>(val ctx: T, val dependencies: BotDependencies)

fun TelegramConfig.toBotSettings() =
    BotSettings(creatorId, username)
