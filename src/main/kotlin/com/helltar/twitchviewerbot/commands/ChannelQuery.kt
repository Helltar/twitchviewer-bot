package com.helltar.twitchviewerbot.commands

/**
 * The channels the arguments of /clip or /screenshot ask for. Each argument is a channel name (`emiru`),
 * a prefix over the user's list (`em.`) or a prefix to leave out (`!em.`).
 *
 * The named channels and the listed channels matching a prefix are taken together, or the whole list when
 * there are neither; the exclusions are then removed from that, named channels included. Arguments that
 * contradict each other, such as `em. !em.`, leave nothing.
 */
class ChannelQuery private constructor(
    private val named: List<String>,
    private val prefixes: List<String>,
    private val exclusions: List<String>
) {

    sealed interface ParseResult {
        data class Valid(val query: ChannelQuery) : ParseResult

        /** Arguments that are neither a channel name nor a prefix, in the order given. */
        data class Invalid(val arguments: List<String>) : ParseResult

        data object TooMany : ParseResult
    }

    companion object {
        const val MAX_ARGUMENTS = 32

        // twitch's own rules for a login; a prefix is any non-empty start of one
        private val NAME_CHARS = Regex("[a-zA-Z0-9_]+")
        private val NAME_LENGTH = 2..25
        private val PREFIX_LENGTH = 1..25

        fun parse(arguments: List<String>): ParseResult {
            if (arguments.size > MAX_ARGUMENTS)
                return ParseResult.TooMany

            val named = mutableListOf<String>()
            val prefixes = mutableListOf<String>()
            val exclusions = mutableListOf<String>()
            val invalid = mutableListOf<String>()

            for (argument in arguments) {
                val (target, value) =
                    when {
                        argument.startsWith("!") && argument.endsWith(".") ->
                            exclusions to argument.substring(1, argument.length - 1).takeIf(::isPrefix)

                        argument.endsWith(".") ->
                            prefixes to argument.removeSuffix(".").takeIf(::isPrefix)

                        else -> named to argument.takeIf(::isName)
                    }

                if (value != null)
                    target += value.lowercase()
                else
                    invalid += argument
            }

            if (invalid.isNotEmpty())
                return ParseResult.Invalid(invalid)

            return ParseResult.Valid(ChannelQuery(named.distinct(), prefixes.distinct(), exclusions.distinct()))
        }

        private fun isName(value: String) =
            value.length in NAME_LENGTH && value.matches(NAME_CHARS)

        private fun isPrefix(value: String) =
            value.length in PREFIX_LENGTH && value.matches(NAME_CHARS)
    }

    fun select(userChannels: List<String>): ChannelSelection {
        val listed = userChannels.map { it.lowercase() }

        val included =
            if (named.isEmpty() && prefixes.isEmpty())
                listed
            else
                named + listed.filter { channel -> prefixes.any { channel.startsWith(it) } }

        val channels = included.filterNot { channel -> exclusions.any { channel.startsWith(it) } }.distinct()

        return ChannelSelection(channels, named.filter { it in channels }.toSet())
    }
}

/** The channels to look up, and which of them were asked for by name rather than taken from the list. */
data class ChannelSelection(val channels: List<String>, val named: Set<String> = emptySet())
