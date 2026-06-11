package com.helltar.twitchviewerbot.commands

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Localization
import com.helltar.twitchviewerbot.bot.BotContext
import com.helltar.twitchviewerbot.bot.BotDependencies
import com.helltar.twitchviewerbot.database.dao.userChannelsDao
import com.helltar.twitchviewerbot.twitch.StreamInfo
import com.helltar.twitchviewerbot.text.toHashTag
import com.helltar.twitchviewerbot.text.toTwitchHtmlLink

abstract class TwitchCommand(botContext: BotContext<MessageContext>) :
    BotCommand(botContext.ctx, botContext.actor.id, botContext.actor.languageCode) {

    protected val dependencies: BotDependencies = botContext.dependencies
    protected val twitchService = dependencies.twitchService

    protected suspend fun loadUserChannels(userId: Long = this.userId) =
        userChannelsDao.list(userId)

    protected fun checkChannelNameAndReplyIfInvalid(name: String): Boolean {
        if (name.length !in 2..25) {
            replyToMessage(localizedString(Localization.INVALID_CHANNEL_NAME_LENGTH))
            return false
        }

        if (!name.matches("^[a-zA-Z0-9_]*$".toRegex())) {
            replyToMessage(localizedString(Localization.INVALID_CHANNEL_NAME))
            return false
        }

        return true
    }

    protected fun createHtmlCaption(stream: StreamInfo): String {
        val username = stream.username
        val category = stream.gameName

        val title = "${stream.login.toTwitchHtmlLink(username)} - ${stream.title}\n\n"
        val categoryTag = if (category.isNotEmpty()) ", #${category.toHashTag()}" else ""
        val uptime = localizedString(Localization.STREAM_UPTIME).format(stream.uptime) + "\n\n"
        val viewersCount = localizedString(Localization.STREAM_VIEWERS).format(stream.viewerCount) + "\n"

        return "$title$viewersCount$uptime#$username$categoryTag"
    }
}
