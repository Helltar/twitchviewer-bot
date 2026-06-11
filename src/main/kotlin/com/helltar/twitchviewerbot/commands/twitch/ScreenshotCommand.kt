package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Localization
import com.helltar.twitchviewerbot.bot.BotContext
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.twitch.StreamInfo
import com.helltar.twitchviewerbot.coroutines.runCatchingPreservingCancellation
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto

class ScreenshotCommand(botContext: BotContext<MessageContext>) : TwitchCommand(botContext) {

    override suspend fun run() {
        if (arguments.isEmpty()) {
            val channels = loadUserChannels()

            if (channels.isNotEmpty())
                fetchAndSendScreenshots(channels)
            else
                replyToMessage(localizedString(Localization.SCREENSHOT_COMMAND_INFO))
        } else {
            val channel = arguments.first()

            if (checkChannelNameAndReplyIfInvalid(channel))
                fetchAndSendScreenshots(listOf(channel))
        }
    }

    fun fetchAndSendScreenshots(channels: List<String>) {
        val tempMessageId = replyToMessage(localizedString(Localization.WAIT_CHECK_ONLINE))

        try {
            val liveList =
                runCatchingPreservingCancellation { twitchService.fetchActiveStreams(channels) }
                    .getOrElse {
                        replyToMessage(localizedString(Localization.TWITCH_EXCEPTION))
                        return
                    }

            if (liveList.isNotEmpty()) {
                val chunks = liveList.chunked(10)

                chunks.forEach { chunk ->
                    if (chunk.size > 1)
                        replyToMessageWithMediaGroup(chunk.map { buildMediaPhoto(it) })
                    else {
                        val stream = chunk.first()
                        replyToMessageWithPhoto(stream.thumbnailUrl, createHtmlCaption(stream))
                    }
                }
            } else
                replyToMessage(localizedString(Localization.EMPTY_ONLINE_LIST))
        } finally {
            deleteMessageAsync(tempMessageId)
        }
    }

    private fun buildMediaPhoto(stream: StreamInfo): InputMediaPhoto =
        InputMediaPhoto.builder()
            .media(stream.thumbnailUrl)
            .caption(createHtmlCaption(stream))
            .parseMode(ParseMode.HTML)
            .build()
}
