package com.helltar.twitchviewerbot.commands.twitch.menu

import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.Strings.localizedString
import com.helltar.twitchviewerbot.bot.BotDependencies
import com.helltar.twitchviewerbot.coroutines.runCatchingPreservingCancellation
import com.helltar.twitchviewerbot.database.dao.userChannelsDao
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import java.util.concurrent.ConcurrentHashMap

val CLIP_DURATION_PRESETS = listOf(15, 30, 45, 60, 120)

private class CachedLiveLogins(val logins: Set<String>, val expiresAt: Long)

class ChannelMenu(
    private val dependencies: BotDependencies,
    private val ownerId: Long,
    private val languageCode: String?
) {
    private companion object {
        const val CHANNELS_PER_PAGE = 8
        const val CHANNELS_PER_ROW = 2
        const val DURATIONS_PER_ROW = 3

        const val STYLE_DEFAULT = ""
        const val STYLE_PRIMARY = "primary"
        const val STYLE_SUCCESS = "success"
        const val STYLE_DANGER = "danger"

        const val ICON_PREV_PAGE = "⬅️"
        const val ICON_NEXT_PAGE = "➡️"

        // live status is cached per owner so paging/back/delete within one menu
        // session reuse a single Twitch lookup instead of querying on every click
        const val LIVE_CACHE_TTL_MS = 30_000L
        val liveCache = ConcurrentHashMap<Long, CachedLiveLogins>()
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

        keyboard.keyboardRow(
            InlineKeyboardRow(button(label(Strings.BTN_SETTINGS), MenuAction.OPEN_SETTINGS, page = currentPage))
        )

        return keyboard.keyboardRow(InlineKeyboardRow(closeButton())).build()
    }

    fun settingsMarkup(currentDuration: Int, page: Int): InlineKeyboardMarkup {
        val keyboard = InlineKeyboardMarkup.builder()

        CLIP_DURATION_PRESETS
            .map { duration ->
                val selected = duration == currentDuration
                button(
                    if (selected) "✅ ${duration}s" else "${duration}s",
                    MenuAction.SET_DURATION,
                    page = page,
                    value = duration,
                    style = if (selected) STYLE_SUCCESS else STYLE_DEFAULT
                )
            }
            .chunked(DURATIONS_PER_ROW)
            .forEach { row -> keyboard.keyboardRow(InlineKeyboardRow(row)) }

        return keyboard
            .keyboardRow(
                InlineKeyboardRow(
                    button(label(Strings.BTN_BACK), MenuAction.BACK, page = page),
                    closeButton()
                )
            )
            .build()
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
                    ),
                    button(
                        label(Strings.BTN_AUDIO_CLIP),
                        MenuAction.CLIP_AUDIO_ONE,
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
                    button(label(Strings.BTN_CLOSE_LIST), MenuAction.CLOSE),
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

    private fun fetchLiveLogins(channels: List<String>): Set<String> {
        val now = System.currentTimeMillis()

        liveCache[ownerId]?.let { cached ->
            if (now < cached.expiresAt) return cached.logins
        }

        val streams =
            runCatchingPreservingCancellation { dependencies.twitchService.fetchActiveStreams(channels) }
                .getOrElse { return emptySet() }

        val logins = streams.mapTo(mutableSetOf()) { it.login.lowercase() }
        liveCache[ownerId] = CachedLiveLogins(logins, now + LIVE_CACHE_TTL_MS)

        return logins
    }

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
        style: String = STYLE_DEFAULT,
        value: Int? = null
    ): InlineKeyboardButton {
        val callbackData = MenuCallback(action, ownerId, page, live, channel, value).serialize()
        return InlineKeyboardButton.builder().text(text).style(style).callbackData(callbackData).build()
    }

    private fun label(key: String): String =
        localizedString(key, languageCode)
}
