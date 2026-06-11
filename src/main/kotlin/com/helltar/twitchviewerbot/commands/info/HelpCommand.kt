package com.helltar.twitchviewerbot.commands.info

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Localization
import com.helltar.twitchviewerbot.commands.BotCommand

class HelpCommand(ctx: MessageContext) : BotCommand(ctx) {

    override suspend fun run() {
        replyToMessage(localizedString(Localization.START_COMMAND_INFO))
    }
}