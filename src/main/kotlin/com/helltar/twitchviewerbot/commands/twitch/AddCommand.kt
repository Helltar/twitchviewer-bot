package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Config.botUsername
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.database.dao.userChannelsDao

class AddCommand(ctx: MessageContext) : TwitchCommand(ctx) {

    private companion object {
        const val MAX_USER_CHANNELS = 32
    }

    override suspend fun run() {
        if (ctx.user().isBot)
            return

        if (arguments.isNotEmpty())
            add(arguments.first())
        else
            replyToMessage(localizedString(Strings.ADD_COMMAND_INFO).format(botUsername))
    }

    private suspend fun add(channel: String) {
        if (!checkChannelNameAndReplyIfInvalid(channel))
            return

        val userChannelsListSize = loadUserChannels().size

        if (userChannelsListSize < MAX_USER_CHANNELS) {
            if (addChannelToUserList(channel))
                replyToMessage(localizedString(Strings.CHANNEL_ADDED_TO_LIST).format(channel, botUsername))
            else
                replyToMessage(localizedString(Strings.CHANNEL_ALREADY_EXISTS_IN_LIST).format(channel))
        } else
            replyToMessage(localizedString(Strings.LIST_FULL).format(botUsername))
    }

    private suspend fun addChannelToUserList(channel: String) =
        userChannelsDao.add(userId, channel.lowercase())
}
