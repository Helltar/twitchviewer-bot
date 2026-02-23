package com.helltar.twitchviewerbot.commands.twitch.keyboard

import com.annimon.tgbotsmodule.commands.CommandBundle
import com.annimon.tgbotsmodule.commands.CommandRegistry
import com.annimon.tgbotsmodule.commands.SimpleCallbackQueryCommand
import com.annimon.tgbotsmodule.commands.authority.For
import com.annimon.tgbotsmodule.commands.context.CallbackQueryContext
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.Strings.localizedString
import com.helltar.twitchviewerbot.bot.CommandExecutor
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_BACK
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_CHANNEL
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_CLIP
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_CLIPS
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_CLOSE_LIST
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_DELETE_CHANNEL
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_NEXT_PAGE
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_PREV_PAGE
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_SCREEN
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.parseOwnerId
import io.github.oshai.kotlinlogging.KotlinLogging

class KeyboardBundle : CommandBundle<For> {

    private companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun register(registry: CommandRegistry<For>) {
        registry.run {
            splitCallbackCommandByWhitespace()
            register(SimpleCallbackQueryCommand(BUTTON_BACK) { click(it, BUTTON_BACK) })
            register(SimpleCallbackQueryCommand(BUTTON_CHANNEL) { click(it, BUTTON_CHANNEL) })
            register(SimpleCallbackQueryCommand(BUTTON_CLIP) { click(it, BUTTON_CLIP) })
            register(SimpleCallbackQueryCommand(BUTTON_CLIPS) { click(it, BUTTON_CLIPS) })
            register(SimpleCallbackQueryCommand(BUTTON_CLOSE_LIST) { click(it, BUTTON_CLOSE_LIST) })
            register(SimpleCallbackQueryCommand(BUTTON_DELETE_CHANNEL) { click(it, BUTTON_DELETE_CHANNEL) })
            register(SimpleCallbackQueryCommand(BUTTON_SCREEN) { click(it, BUTTON_SCREEN) })
            register(SimpleCallbackQueryCommand(BUTTON_NEXT_PAGE) { click(it, BUTTON_NEXT_PAGE) })
            register(SimpleCallbackQueryCommand(BUTTON_PREV_PAGE) { click(it, BUTTON_PREV_PAGE) })
        }
    }

    private fun click(ctx: CallbackQueryContext, buttonId: String) {
        val ownerId = parseOwnerId(ctx.data())
        val user = ctx.user()

        log.debug { "callback data: ${ctx.data()}" }

        if (user.id != ownerId) {
            val text = localizedString(Strings.DONT_TOUCH_IS_NOT_YOUR_LIST, user.languageCode).format(user.firstName)
            ctx.answer(text).callAsync(ctx.sender)
            return
        }

        val launch =
            CommandExecutor.launch("$buttonId@$ownerId") {
                val inlineKeyboard = InlineKeyboard(ctx.apply { update().message = ctx.message() }, ownerId)

                when (buttonId) {
                    BUTTON_CHANNEL -> inlineKeyboard.channel()
                    BUTTON_CLIP -> inlineKeyboard.clip()
                    BUTTON_CLIPS -> inlineKeyboard.clips()
                    BUTTON_CLOSE_LIST -> inlineKeyboard.close()
                    BUTTON_DELETE_CHANNEL -> inlineKeyboard.deleteChannel()
                    BUTTON_SCREEN -> inlineKeyboard.screenshot()
                    else -> inlineKeyboard.update()
                }
            }

        if (!launch)
            ctx.answer(localizedString(Strings.MANY_REQUEST, user.languageCode)).callAsync(ctx.sender)
    }
}
