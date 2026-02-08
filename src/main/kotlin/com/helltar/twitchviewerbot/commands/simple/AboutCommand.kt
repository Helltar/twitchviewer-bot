package com.helltar.twitchviewerbot.commands.simple

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.commands.BotCommand

class AboutCommand(ctx: MessageContext) : BotCommand(ctx) {

    override suspend fun run() {
        replyToMessage(
            """
            <a href="https://github.com/Helltar/twitchviewer-bot">TwitchViewer Bot</a>
            Contact: https://helltar.com
            Source Code:
        """.trimIndent(), true
        )
    }
}
