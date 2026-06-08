package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.bot.BotContext
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.commands.twitch.menu.ChannelMenu
import com.helltar.twitchviewerbot.database.dao.userChannelsDao

class ListCommand(botContext: BotContext<MessageContext>) : TwitchCommand(botContext) {

    override suspend fun run() {
        if (userChannelsDao.isListEmpty(userId)) {
            replyToMessage(localizedString(Strings.LIST_IS_EMPTY))
            return
        }

        val menu = ChannelMenu(dependencies, userId, ctx.user().languageCode)

        replyToMessage(localizedString(Strings.TITLE_CHOOSE_CHANNEL_OR_ACTION), replyMarkup = menu.mainMarkup())
    }
}
