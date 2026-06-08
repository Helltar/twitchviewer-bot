package com.helltar.twitchviewerbot.bot

import com.helltar.twitchviewerbot.TelegramConfig
import com.helltar.twitchviewerbot.twitch.TwitchService

data class BotSettings(
    val creatorId: Long,
    val username: String
)

data class BotDependencies(
    val settings: BotSettings,
    val twitchService: TwitchService
)

data class Actor(val id: Long, val languageCode: String?)

data class BotContext<T>(val ctx: T, val dependencies: BotDependencies, val actor: Actor)

fun TelegramConfig.toBotSettings() =
    BotSettings(creatorId, username)
