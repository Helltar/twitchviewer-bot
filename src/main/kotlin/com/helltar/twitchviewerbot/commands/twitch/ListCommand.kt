package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.CallbackQueryContext
import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.bot.BotContext
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.string
import com.helltar.twitchviewerbot.commands.twitch.keyboard.InlineKeyboard
import com.helltar.twitchviewerbot.database.dao.userChannelsDao
import org.telegram.telegrambots.meta.api.objects.CallbackQuery

class ListCommand(botContext: BotContext<MessageContext>) : TwitchCommand(botContext) {

    override suspend fun run() {
        if (userChannelsDao.isListEmpty(userId)) {
            replyToMessage(localizedString(Strings.LIST_IS_EMPTY))
            return
        }

        val update =
            ctx.update().apply {
                callbackQuery =
                    CallbackQuery().apply {
                        from = ctx.user()
                        message = ctx.message()
                        data = ButtonCallbacks.CallbackData().string()
                    }
            }

        val callbackQueryContext = CallbackQueryContext(ctx.sender, update, String())
        val inlineKeyboard = InlineKeyboard(BotContext(callbackQueryContext, dependencies), userId)

        replyToMessage(localizedString(Strings.TITLE_CHOOSE_CHANNEL_OR_ACTION), replyMarkup = inlineKeyboard.mainMenu())
    }
}
