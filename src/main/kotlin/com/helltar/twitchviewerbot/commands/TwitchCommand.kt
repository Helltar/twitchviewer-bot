package com.helltar.twitchviewerbot.commands

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.bot.BotContext
import com.helltar.twitchviewerbot.bot.BotDependencies
import com.helltar.twitchviewerbot.database.dao.userChannelsDao
import com.helltar.twitchviewerbot.twitch.BroadcastData
import com.helltar.twitchviewerbot.utils.StringUtils.toHashTag
import com.helltar.twitchviewerbot.utils.StringUtils.toTwitchHtmlLink

abstract class TwitchCommand(botContext: BotContext<MessageContext>) : BotCommand(botContext.ctx) {

    protected val dependencies: BotDependencies = botContext.dependencies
    protected val twitchService = dependencies.twitchService

    protected suspend fun loadUserChannels(userId: Long = this.userId) =
        userChannelsDao.list(userId)

    protected fun checkChannelNameAndReplyIfInvalid(name: String): Boolean {
        if (name.length !in 2..25) {
            replyToMessage(localizedString(Strings.INVALID_CHANNEL_NAME_LENGTH))
            return false
        }

        if (!name.matches("^[a-zA-Z0-9_]*$".toRegex())) {
            replyToMessage(localizedString(Strings.INVALID_CHANNEL_NAME))
            return false
        }

        return true
    }

    protected fun createHtmlCaption(broadcastData: BroadcastData): String {
        val username = broadcastData.username
        val category = broadcastData.gameName

        val title = "${broadcastData.login.toTwitchHtmlLink(username)} - ${broadcastData.title}\n\n"
        val categoryTag = if (category.isNotEmpty()) ", #${category.toHashTag()}" else ""
        val startTime = localizedString(Strings.STREAM_START_TIME).format(broadcastData.uptime) + "\n\n"
        val viewersCount = localizedString(Strings.STREAM_VIEWERS).format(broadcastData.viewerCount) + "\n"

        return "$title$viewersCount$startTime#$username$categoryTag"
    }
}
