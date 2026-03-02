package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Strings
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.twitch.BroadcastData
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import java.net.URI

class ScreenshotCommand(ctx: MessageContext) : TwitchCommand(ctx) {

    override suspend fun run() {
        if (arguments.isEmpty()) {
            val channels = loadUserChannels()

            if (channels.isNotEmpty())
                fetchAndSendScreenshots(channels)
            else
                replyToMessage(localizedString(Strings.SCREENSHOT_COMMAND_INFO))
        } else {
            val channel = arguments.first()

            if (checkChannelNameAndReplyIfInvalid(channel))
                fetchAndSendScreenshots(listOf(channel))
        }
    }

    fun fetchAndSendScreenshots(channels: List<String>) {
        val tempMessageId = replyToMessage(localizedString(Strings.WAIT_CHECK_ONLINE))

        try {
            val liveList =
                runCatching { twitchService.fetchActiveStreams(channels) }
                    .getOrElse {
                        replyToMessage(localizedString(Strings.TWITCH_EXCEPTION))
                        return
                    }

            if (liveList.isNotEmpty()) {
                val chunks = liveList.chunked(10)

                chunks.forEach { chunk ->
                    if (chunk.size > 1)
                        replyToMessageWithMediaGroup(chunk.map { buildMediaPhoto(it) })
                    else {
                        val broadcastData = chunk.first()
                        replyToMessageWithPhoto(broadcastData.thumbnailUrl, createHtmlCaption(broadcastData))
                    }
                }
            } else
                replyToMessage(localizedString(Strings.EMPTY_ONLINE_LIST))
        } finally {
            deleteMessageAsync(tempMessageId)
        }
    }

    private fun buildMediaPhoto(broadcastData: BroadcastData): InputMediaPhoto {
        val caption = createHtmlCaption(broadcastData)
        val inputStream = URI.create(broadcastData.thumbnailUrl).toURL().openStream()

        return InputMediaPhoto.builder()
            .media(inputStream, broadcastData.thumbnailUrl)
            .caption(caption)
            .parseMode(ParseMode.HTML)
            .build()
    }
}
