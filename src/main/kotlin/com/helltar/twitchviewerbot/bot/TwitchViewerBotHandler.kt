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
import com.helltar.twitchviewerbot.commands.twitch.menu.MenuHandler
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
            register(SimpleCommand("/add") { executeCommand(AddCommand(botContext(it))) })
            register(SimpleCommand("/clip") { executeCommand(ClipCommand(botContext(it)), RequestKey.CLIP) })
            register(SimpleCommand("/list") { executeCommand(ListCommand(botContext(it))) })
            register(SimpleCommand("/screenshot") { executeCommand(ScreenshotCommand(botContext(it)), RequestKey.SCREENSHOT) })
            register(SimpleCommand("/cancel") { cancelJobs(it) })

            register(SimpleCommand("/start") { executeCommand(StartCommand(it)) })
            register(SimpleCommand("/about") { executeCommand(AboutCommand(it)) })
            register(SimpleCommand("/help") { executeCommand(HelpCommand(it)) })

            registerBundle(MenuHandler(dependencies))
        }
    }

    override fun onUpdate(update: Update): BotApiMethod<*>? {
        commandRegistry.handleUpdate(this, update)
        return null
    }

    private fun botContext(ctx: MessageContext) =
        BotContext(ctx, dependencies, Actor(ctx.user().id, ctx.user().languageCode))
}
