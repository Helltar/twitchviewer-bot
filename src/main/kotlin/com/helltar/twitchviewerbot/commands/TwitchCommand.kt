package com.helltar.twitchviewerbot.commands

import com.annimon.tgbotsmodule.commands.context.MessageContext
import com.helltar.twitchviewerbot.Localization
import com.helltar.twitchviewerbot.bot.BotContext
import com.helltar.twitchviewerbot.bot.BotDependencies
import com.helltar.twitchviewerbot.coroutines.runCatchingPreservingCancellation
import com.helltar.twitchviewerbot.database.dao.userChannelsDao
import com.helltar.twitchviewerbot.text.escapeHtml
import com.helltar.twitchviewerbot.text.toHashTag
import com.helltar.twitchviewerbot.text.toTwitchHtmlLink
import com.helltar.twitchviewerbot.twitch.StreamInfo

abstract class TwitchCommand(botContext: BotContext<MessageContext>) :
    BotCommand(botContext.ctx, botContext.actor.id, botContext.actor.languageCode) {

    private companion object {
        // an invalid argument is echoed back, so a pasted wall of text can't push the reply past telegram's limit
        const val MAX_ECHOED_ARGUMENT_LENGTH = 30
    }

    protected val dependencies: BotDependencies = botContext.dependencies
    protected val twitchService = dependencies.twitchService

    protected suspend fun loadUserChannels(userId: Long = this.userId) =
        userChannelsDao.list(userId)

    /**
     * Resolves the command's arguments, see [ChannelQuery], against the user's list. Replies and returns
     * null when there is nothing to look up: an invalid argument, an empty list, or a filter nothing matches.
     */
    protected suspend fun selectChannels(emptyListInfoKey: String): ChannelSelection? {
        val query =
            when (val parsed = ChannelQuery.parse(arguments.toList())) {
                is ChannelQuery.ParseResult.Valid -> parsed.query

                is ChannelQuery.ParseResult.Invalid -> {
                    val echoed = parsed.arguments.joinToString { "<code>${it.take(MAX_ECHOED_ARGUMENT_LENGTH).escapeHtml()}</code>" }
                    replyToMessage(localizedString(Localization.INVALID_ARGUMENTS).format(echoed))
                    return null
                }

                ChannelQuery.ParseResult.TooMany -> {
                    replyToMessage(localizedString(Localization.TOO_MANY_ARGUMENTS).format(ChannelQuery.MAX_ARGUMENTS))
                    return null
                }
            }

        val selection = query.select(loadUserChannels())

        if (selection.channels.isEmpty()) {
            if (arguments.isEmpty())
                replyToMessage(localizedString(emptyListInfoKey))
            else
                replyToMessage(localizedString(Localization.FILTER_NO_CHANNELS_FOUND).format(arguments.joinToString(" ").escapeHtml()))

            return null
        }

        return selection
    }

    /**
     * Looks up which of [channels] are live. Channels in [named], asked for by name, are reported when they
     * are not; when nothing at all is live the reply says so. Empty when there is nothing to do, Twitch
     * failing included, every case already answered.
     */
    protected fun fetchLiveStreams(channels: List<String>, named: Set<String> = emptySet()): List<StreamInfo> {
        val streams =
            runCatchingPreservingCancellation { twitchService.fetchActiveStreams(channels) }
                .getOrElse {
                    replyToMessage(localizedString(Localization.TWITCH_EXCEPTION))
                    return emptyList()
                }

        val live = streams.mapTo(HashSet()) { it.login.lowercase() }
        val notLive = named.filterNot { it in live }

        // a list or a filter coming up empty is the usual "nobody is live"; only names alone get named back
        if (streams.isEmpty() && channels.any { it !in named })
            replyToMessage(localizedString(Localization.EMPTY_ONLINE_LIST))
        else if (notLive.isNotEmpty())
            replyToMessage(localizedString(Localization.NOT_LIVE).format(notLive.joinToString { it.toTwitchHtmlLink(it) }))

        return streams
    }

    protected fun checkChannelNameAndReplyIfInvalid(name: String): Boolean {
        if (name.length !in 2..25) {
            replyToMessage(localizedString(Localization.INVALID_CHANNEL_NAME_LENGTH))
            return false
        }

        if (!name.matches("^[a-zA-Z0-9_]*$".toRegex())) {
            replyToMessage(localizedString(Localization.INVALID_CHANNEL_NAME))
            return false
        }

        return true
    }

    protected fun createHtmlCaption(stream: StreamInfo): String {
        val username = stream.username
        val category = stream.gameName

        val title = "${stream.login.toTwitchHtmlLink(username)} - ${stream.title}\n\n"
        val categoryTag = if (category.isNotEmpty()) ", #${category.toHashTag()}" else ""
        val uptime = localizedString(Localization.STREAM_UPTIME).format(stream.uptime) + "\n\n"
        val viewersCount = localizedString(Localization.STREAM_VIEWERS).format(stream.viewerCount) + "\n"

        return "$title$viewersCount$uptime#$username$categoryTag"
    }
}
