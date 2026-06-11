package com.helltar.twitchviewerbot.commands.simple

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.LocalizationKeys
import com.helltar.twitchviewerbot.commands.BotCommand

class StartCommand(ctx: MessageContext) : BotCommand(ctx) {

    override suspend fun run() {
        replyToMessage(localizedString(LocalizationKeys.START_COMMAND_INFO))
    }
}