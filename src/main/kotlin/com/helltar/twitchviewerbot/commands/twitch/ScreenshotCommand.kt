package com.helltar.twitchviewerbot.commands.twitch

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Localization
import com.helltar.twitchviewerbot.bot.BotContext
import com.helltar.twitchviewerbot.commands.TwitchCommand
import com.helltar.twitchviewerbot.twitch.StreamInfo
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto

class ScreenshotCommand(botContext: BotContext<MessageContext>) : TwitchCommand(botContext) {

    override suspend fun run() {
        val selection = selectChannels(Localization.SCREENSHOT_COMMAND_INFO) ?: return
        fetchAndSendScreenshots(selection.channels, selection.named)
    }

    fun fetchAndSendScreenshots(channels: List<String>, named: Set<String> = emptySet()) {
        val tempMessageId = replyToMessage(localizedString(Localization.WAIT_CHECK_ONLINE))

        try {
            fetchLiveStreams(channels, named)
                .chunked(10)
                .forEach { chunk ->
                    if (chunk.size > 1)
                        replyToMessageWithMediaGroup(chunk.map { buildMediaPhoto(it) })
                    else {
                        val stream = chunk.first()
                        replyToMessageWithPhoto(stream.thumbnailUrl, createHtmlCaption(stream))
                    }
                }
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
