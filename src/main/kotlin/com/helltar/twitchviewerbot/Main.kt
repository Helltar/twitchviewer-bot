package com.helltar.twitchviewerbot

import com.annimon.tgbotsmodule.Runner
import com.helltar.twitchviewerbot.bot.BotDependencies
import com.helltar.twitchviewerbot.bot.TwitchViewerBot
import com.helltar.twitchviewerbot.bot.toBotSettings
import com.helltar.twitchviewerbot.database.Database
import com.helltar.twitchviewerbot.twitch.Twitch4jService

fun main(args: Array<String>) {
    Database.init(Config.database)

    val telegram = Config.telegram

    val dependencies =
        BotDependencies(
            settings = telegram.toBotSettings(),
            twitchService = Twitch4jService(Config.twitch)
        )

    Runner.run(args.firstOrNull().orEmpty(), listOf(TwitchViewerBot(telegram.token, dependencies)))
}
