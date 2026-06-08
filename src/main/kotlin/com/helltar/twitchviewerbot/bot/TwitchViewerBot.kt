package com.helltar.twitchviewerbot.bot

import com.annimon.tgbotsmodule.BotModule
import com.annimon.tgbotsmodule.BotModuleOptions
import com.annimon.tgbotsmodule.beans.Config

class TwitchViewerBot(private val botToken: String, private val dependencies: BotDependencies) : BotModule {

    override fun botHandler(_config: Config) =
        TwitchViewerBotHandler(BotModuleOptions.createDefault(botToken), dependencies)
}
