package com.helltar.twitchviewerbot.commands.twitch.menu

import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.Strings.localizedString
import com.helltar.twitchviewerbot.bot.BotDependencies
import com.helltar.twitchviewerbot.database.dao.userChannelsDao
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow

class ChannelMenu(
    private val dependencies: BotDependencies,
    private val ownerId: Long,
    private val languageCode: String?
) {
    private companion object {
        const val CHANNELS_PER_PAGE = 8
        const val CHANNELS_PER_ROW = 2

        const val STYLE_DEFAULT = ""
        const val STYLE_PRIMARY = "primary"
        const val STYLE_SUCCESS = "success"
        const val STYLE_DANGER = "danger"

        const val ICON_PREV_PAGE = "⬅️"
        const val ICON_NEXT_PAGE = "➡️"
    }

    suspend fun mainMarkup(page: Int = 0): InlineKeyboardMarkup {
        val channels = userChannelsDao.list(ownerId)
        val liveLogins = fetchLiveLogins(channels)
        val keyboard = InlineKeyboardMarkup.builder()

        val pages = channels.sortedByDescending { it.lowercase() in liveLogins }.chunked(CHANNELS_PER_PAGE)

        if (pages.isEmpty())
            return keyboard.keyboardRow(InlineKeyboardRow(closeButton())).build()

        val currentPage = page.coerceIn(0, pages.lastIndex)

        pages[currentPage]
            .map { channel ->
                val live = channel.lowercase() in liveLogins
                button(
                    channel,
                    MenuAction.OPEN_CHANNEL,
                    channel,
                    live,
                    currentPage,
                    if (live) STYLE_SUCCESS else STYLE_DEFAULT
                )
            }
            .chunked(CHANNELS_PER_ROW)
            .forEach { row -> keyboard.keyboardRow(InlineKeyboardRow(row)) }

        navigationRow(currentPage, pages.lastIndex)?.let { keyboard.keyboardRow(it) }

        if (liveLogins.isNotEmpty()) {
            keyboard.keyboardRow(
                InlineKeyboardRow(
                    button(
                        label(Strings.BTN_RECORD_ALL),
                        MenuAction.CLIP_ALL,
                        page = currentPage,
                        style = STYLE_PRIMARY
                    ),
                    button(
                        label(Strings.BTN_CAPTURE_ALL),
                        MenuAction.SCREENSHOT_ALL,
                        page = currentPage,
                        style = STYLE_PRIMARY
                    )
                )
            )
        }

        return keyboard.keyboardRow(InlineKeyboardRow(closeButton())).build()
    }

    fun channelMarkup(channel: String, live: Boolean, page: Int): InlineKeyboardMarkup {
        val keyboard = InlineKeyboardMarkup.builder()

        if (live) {
            keyboard.keyboardRow(
                InlineKeyboardRow(
                    button(
                        label(Strings.BTN_SHORT_CLIP),
                        MenuAction.CLIP_ONE,
                        channel,
                        page = page,
                        style = STYLE_PRIMARY
                    )
                )
            )
        }

        return keyboard
            .keyboardRow(
                InlineKeyboardRow(
                    button(label(Strings.BTN_BACK), MenuAction.BACK, page = page),
                    button(label(Strings.BTN_EXIT), MenuAction.CLOSE),
                    button(
                        label(Strings.BTN_DELETE),
                        MenuAction.DELETE_CHANNEL,
                        channel,
                        page = page,
                        style = STYLE_DANGER
                    )
                )
            )
            .build()
    }

    private fun fetchLiveLogins(channels: List<String>): Set<String> =
        runCatching { dependencies.twitchService.fetchActiveStreams(channels) }
            .getOrDefault(emptyList())
            .mapTo(mutableSetOf()) { it.login.lowercase() }

    private fun navigationRow(currentPage: Int, lastPage: Int): InlineKeyboardRow? {
        val buttons =
            buildList {
                if (currentPage > 0) add(button(ICON_PREV_PAGE, MenuAction.PAGE, page = currentPage - 1))
                if (currentPage < lastPage) add(button(ICON_NEXT_PAGE, MenuAction.PAGE, page = currentPage + 1))
            }

        return if (buttons.isEmpty()) null else InlineKeyboardRow(buttons)
    }

    private fun closeButton(): InlineKeyboardButton =
        button(label(Strings.BTN_CLOSE_LIST), MenuAction.CLOSE)

    private fun button(
        text: String,
        action: MenuAction,
        channel: String? = null,
        live: Boolean = false,
        page: Int = 0,
        style: String = STYLE_DEFAULT
    ): InlineKeyboardButton {
        val callbackData = MenuCallback(action, ownerId, page, live, channel).serialize()
        return InlineKeyboardButton.builder().text(text).style(style).callbackData(callbackData).build()
    }

    private fun label(key: String): String =
        localizedString(key, languageCode)
}
