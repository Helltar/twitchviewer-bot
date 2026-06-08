package com.helltar.twitchviewerbot.commands

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Strings
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import java.io.File
import java.net.URI

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

    protected fun replyToMessageWithMediaGroup(media: List<InputMediaPhoto>) {
        ctx.replyWithMediaGroup()
            .setMedias(media)
            .setReplyToMessageId(ctx.messageId())
            .call(ctx.sender)
    }

    protected fun replyToMessageWithPhoto(url: String, caption: String): Message =
        ctx.replyToMessageWithPhoto()
            .setFile(url, URI.create(url).toURL().openStream())
            .setCaption(caption)
            .setParseMode(ParseMode.HTML)
            .call(ctx.sender)

    protected fun replyToMessageWithVideo(filename: String, caption: String): Message =
        ctx.replyToMessageWithVideo()
            .setFile(File(filename))
            .setCaption(caption)
            .setParseMode(ParseMode.HTML)
            .call(ctx.sender)

    protected fun deleteMessageAsync(messageId: Int) {
        ctx.deleteMessage()
            .setMessageId(messageId)
            .callAsync(ctx.sender)
    }

    protected fun localizedString(key: String) =
        Strings.localizedString(key, languageCode)
}
