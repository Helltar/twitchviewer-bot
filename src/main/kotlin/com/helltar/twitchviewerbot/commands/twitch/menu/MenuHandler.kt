package com.helltar.twitchviewerbot.commands.twitch.menu

import com.annimon.tgbotsmodule.commands.CommandBundle
import com.annimon.tgbotsmodule.commands.CommandRegistry
import com.annimon.tgbotsmodule.commands.SimpleCallbackQueryCommand
import com.annimon.tgbotsmodule.commands.authority.For
import com.annimon.tgbotsmodule.commands.context.CallbackQueryContext
import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.Strings.localizedString
import com.helltar.twitchviewerbot.bot.*
import com.helltar.twitchviewerbot.commands.twitch.ClipCommand
import com.helltar.twitchviewerbot.commands.twitch.ClipFormat
import com.helltar.twitchviewerbot.commands.twitch.ScreenshotCommand
import com.helltar.twitchviewerbot.database.dao.userChannelsDao
import com.helltar.twitchviewerbot.database.dao.usersDao
import com.helltar.twitchviewerbot.utils.StringUtils.toTwitchHtmlLink
import io.github.oshai.kotlinlogging.KotlinLogging
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup

class MenuHandler(private val dependencies: BotDependencies) : CommandBundle<For> {

    private companion object {
        val log = KotlinLogging.logger {}
    }

    override fun register(registry: CommandRegistry<For>) {
        registry.splitCallbackCommandByWhitespace()

        MenuAction.entries.forEach { action ->
            registry.register(SimpleCallbackQueryCommand(action.code) { onClick(it) })
        }
    }

    private fun onClick(ctx: CallbackQueryContext) {
        val callback = MenuCallback.parse(ctx.data()) ?: return
        val user = ctx.user()

        log.debug { "menu callback: ${ctx.data()}" }

        if (user.id != callback.ownerId) {
            ctx.answer(localizedString(Strings.DONT_TOUCH_IS_NOT_YOUR_LIST, user.languageCode).format(user.firstName))
                .callAsync(ctx.sender)

            return
        }

        val started = CommandExecutor.launch(requestKey(callback)) { dispatch(ctx, callback) }

        // acknowledge immediately so the client stops showing the button spinner;
        // long-running actions (clip/screenshot) keep working in the launched job
        if (started)
            ctx.answer("").callAsync(ctx.sender)
        else
            ctx.answer(localizedString(Strings.MANY_REQUEST, user.languageCode)).callAsync(ctx.sender)
    }

    private suspend fun dispatch(ctx: CallbackQueryContext, callback: MenuCallback) {
        val ownerId = callback.ownerId
        val languageCode = usersDao.languageCode(ownerId)
        val menu = ChannelMenu(dependencies, ownerId, languageCode)

        when (callback.action) {
            MenuAction.OPEN_CHANNEL -> openChannel(ctx, callback, menu, languageCode)
            MenuAction.BACK, MenuAction.PAGE -> showMenu(ctx, menu, ownerId, callback.page, languageCode)

            MenuAction.DELETE_CHANNEL -> {
                callback.channel?.let { userChannelsDao.delete(ownerId, it) }
                showMenu(ctx, menu, ownerId, callback.page, languageCode)
            }

            MenuAction.CLOSE ->
                editMessage(
                    ctx,
                    localizedString(Strings.USER_CLOSE_LIST, languageCode).format(
                        ctx.user().firstName,
                        dependencies.settings.username
                    )
                )

            MenuAction.OPEN_SETTINGS -> showSettings(ctx, menu, ownerId, callback.page, languageCode)

            MenuAction.SET_DURATION -> {
                val duration = callback.value

                if (duration != null && duration in CLIP_DURATION_PRESETS)
                    usersDao.setClipDuration(ownerId, duration)

                showSettings(ctx, menu, ownerId, callback.page, languageCode)
            }

            MenuAction.CLIP_ONE -> callback.channel?.let { clipCommand(ctx, ownerId, languageCode).clip(it) }

            MenuAction.CLIP_AUDIO_ONE ->
                callback.channel?.let { clipCommand(ctx, ownerId, languageCode, ClipFormat.AUDIO).clip(it) }

            MenuAction.CLIP_ALL -> withChannels(ownerId) {
                clipCommand(
                    ctx,
                    ownerId,
                    languageCode
                ).fetchAndSendClips(it)
            }

            MenuAction.SCREENSHOT_ALL -> withChannels(ownerId) {
                screenshotCommand(
                    ctx,
                    ownerId,
                    languageCode
                ).fetchAndSendScreenshots(it)
            }
        }
    }

    private fun openChannel(
        ctx: CallbackQueryContext,
        callback: MenuCallback,
        menu: ChannelMenu,
        languageCode: String?
    ) {
        val channel = callback.channel ?: return

        val title =
            localizedString(Strings.TITLE_CHANNEL_IS_SELECTED, languageCode).format(channel.toTwitchHtmlLink(channel))

        editMessage(ctx, title, menu.channelMarkup(channel, callback.live, callback.page))
    }

    private suspend fun showMenu(
        ctx: CallbackQueryContext,
        menu: ChannelMenu,
        ownerId: Long,
        page: Int,
        languageCode: String?
    ) {
        if (userChannelsDao.isListEmpty(ownerId))
            editMessage(ctx, localizedString(Strings.LIST_IS_EMPTY, languageCode))
        else
            editMessage(
                ctx,
                localizedString(Strings.TITLE_CHOOSE_CHANNEL_OR_ACTION, languageCode),
                menu.mainMarkup(page)
            )
    }

    private suspend fun showSettings(
        ctx: CallbackQueryContext,
        menu: ChannelMenu,
        ownerId: Long,
        page: Int,
        languageCode: String?
    ) {
        val duration = usersDao.clipDuration(ownerId)

        editMessage(
            ctx,
            localizedString(Strings.TITLE_SETTINGS, languageCode).format(duration),
            menu.settingsMarkup(duration, page)
        )
    }

    private suspend inline fun withChannels(ownerId: Long, action: (List<String>) -> Unit) {
        val channels = userChannelsDao.list(ownerId)
        if (channels.isNotEmpty()) action(channels)
    }

    private fun clipCommand(
        ctx: CallbackQueryContext,
        ownerId: Long,
        languageCode: String?,
        format: ClipFormat = ClipFormat.VIDEO
    ): ClipCommand =
        ClipCommand(commandContext(ctx, ownerId, languageCode), format)

    private fun screenshotCommand(ctx: CallbackQueryContext, ownerId: Long, languageCode: String?): ScreenshotCommand =
        ScreenshotCommand(commandContext(ctx, ownerId, languageCode))

    private fun commandContext(
        ctx: CallbackQueryContext,
        ownerId: Long,
        languageCode: String?
    ): BotContext<MessageContext> =
        BotContext(
            MessageContext(ctx.sender, Update().apply { message = ctx.message() }, ""),
            dependencies,
            Actor(ownerId, languageCode)
        )

    private fun editMessage(ctx: CallbackQueryContext, text: String, replyMarkup: InlineKeyboardMarkup? = null) =
        ctx.editMessage(text, replyMarkup)
            .setParseMode(ParseMode.HTML)
            .disableWebPagePreview()
            .call(ctx.sender)

    private fun requestKey(callback: MenuCallback): String =
        when (callback.action) {
            MenuAction.CLIP_ONE, MenuAction.CLIP_AUDIO_ONE, MenuAction.CLIP_ALL ->
                RequestKey.forUser(RequestKey.CLIP, callback.ownerId)

            MenuAction.SCREENSHOT_ALL -> RequestKey.forUser(RequestKey.SCREENSHOT, callback.ownerId)

            else -> RequestKey.forUser(RequestKey.MENU, callback.ownerId)
        }
}
