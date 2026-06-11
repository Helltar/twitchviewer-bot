package com.helltar.twitchviewerbot.commands

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.LocalizationKeys
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import java.io.File

abstract class BotCommand(
    val ctx: MessageContext,
    protected val userId: Long = ctx.user().id,
    private val languageCode: String? = ctx.user().languageCode
) {

    protected val arguments: Array<String> = ctx.arguments()

    abstract suspend fun run()

    fun replyToMessage(text: String, webPagePreview: Boolean = false, replyMarkup: InlineKeyboardMarkup? = null): Int =
        ctx.replyToMessage(text)
            .setReplyMarkup(replyMarkup)
            .setParseMode(ParseMode.HTML)
            .setWebPagePreviewEnabled(webPagePreview)
            .call(ctx.sender)
            .messageId

    fun replyToMessageAsync(text: String, webPagePreview: Boolean = false, replyMarkup: InlineKeyboardMarkup? = null) {
        ctx.replyToMessage(text)
            .setReplyMarkup(replyMarkup)
            .setParseMode(ParseMode.HTML)
            .setWebPagePreviewEnabled(webPagePreview)
            .callAsync(ctx.sender)
    }

    protected fun replyToMessageWithMediaGroup(media: List<InputMediaPhoto>) {
        ctx.replyWithMediaGroup()
            .setMedias(media)
            .setReplyToMessageId(ctx.messageId())
            .call(ctx.sender)
    }

    protected fun replyToMessageWithPhoto(url: String, caption: String): Message =
        ctx.replyToMessageWithPhoto()
            .setFile(url)
            .setCaption(caption)
            .setParseMode(ParseMode.HTML)
            .call(ctx.sender)

    protected fun replyToMessageWithVideo(filename: String, displayName: String, caption: String): Message =
        ctx.replyToMessageWithVideo()
            .setFile(InputFile(File(filename), displayName))
            .setCaption(caption)
            .setParseMode(ParseMode.HTML)
            .setSupportsStreaming(true) // hint Telegram to show an inline player; dimensions/duration are read from the file
            .call(ctx.sender)

    protected fun replyToMessageWithAudio(
        filename: String,
        displayName: String,
        performer: String,
        caption: String,
        durationSec: Int
    ): Message =
        ctx.replyToMessageWithAudio()
            .setFile(InputFile(File(filename), displayName))
            .setTitle(displayName.substringBeforeLast('.'))
            .setPerformer(performer)
            .setDuration(durationSec)
            .setCaption(caption)
            .setParseMode(ParseMode.HTML)
            .call(ctx.sender)

    protected fun deleteMessageAsync(messageId: Int) {
        ctx.deleteMessage()
            .setMessageId(messageId)
            .callAsync(ctx.sender)
    }

    protected fun localizedString(key: String) =
        LocalizationKeys.localizedString(key, languageCode)
}
