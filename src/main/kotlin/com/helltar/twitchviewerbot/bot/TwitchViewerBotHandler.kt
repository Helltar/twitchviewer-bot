package com.helltar.twitchviewerbot.bot

import com.annimon.tgbotsmodule.BotHandler
import com.annimon.tgbotsmodule.BotModuleOptions
import com.annimon.tgbotsmodule.commands.CommandRegistry
import com.annimon.tgbotsmodule.commands.SimpleCommand
import com.annimon.tgbotsmodule.commands.authority.SimpleAuthority
import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.bot.CommandExecutor.cancelJobs
import com.helltar.twitchviewerbot.bot.CommandExecutor.executeCommand
import com.helltar.twitchviewerbot.commands.simple.AboutCommand
import com.helltar.twitchviewerbot.commands.simple.HelpCommand
import com.helltar.twitchviewerbot.commands.simple.StartCommand
import com.helltar.twitchviewerbot.commands.twitch.AddCommand
import com.helltar.twitchviewerbot.commands.twitch.ClipCommand
import com.helltar.twitchviewerbot.commands.twitch.ListCommand
import com.helltar.twitchviewerbot.commands.twitch.ScreenshotCommand
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_CLIPS
import com.helltar.twitchviewerbot.commands.twitch.keyboard.ButtonCallbacks.BUTTON_SCREEN
import com.helltar.twitchviewerbot.commands.twitch.keyboard.KeyboardBundle
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod
import org.telegram.telegrambots.meta.api.objects.Update

class TwitchViewerBotHandler(
    botModuleOptions: BotModuleOptions,
    private val dependencies: BotDependencies
) : BotHandler(botModuleOptions) {

    private val authority = SimpleAuthority(dependencies.settings.creatorId)
    private val commandRegistry = CommandRegistry(dependencies.settings.username, authority)

    init {
        commandRegistry.run {
            register(SimpleCommand("/start") { executeCommand(StartCommand(it)) })
            register(SimpleCommand("/help") { executeCommand(HelpCommand(it)) })
            register(SimpleCommand("/about") { executeCommand(AboutCommand(it)) })

            register(SimpleCommand("/clip") { executeCommand(ClipCommand(botContext(it)), BUTTON_CLIPS) })
            register(SimpleCommand("/screenshot") { executeCommand(ScreenshotCommand(botContext(it)), BUTTON_SCREEN) })
            register(SimpleCommand("/add") { executeCommand(AddCommand(botContext(it))) })
            register(SimpleCommand("/list") { executeCommand(ListCommand(botContext(it))) })
            register(SimpleCommand("/cancel") { cancelJobs(it) })

            registerBundle(KeyboardBundle(dependencies))
        }
    }

    override fun onUpdate(update: Update): BotApiMethod<*>? {
        commandRegistry.handleUpdate(this, update)
        return null
    }

    private fun botContext(ctx: MessageContext) =
        BotContext(ctx, dependencies)
}
