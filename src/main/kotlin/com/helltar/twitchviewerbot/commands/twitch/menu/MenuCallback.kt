package com.helltar.twitchviewerbot.commands.twitch.menu

data class MenuCallback(
    val action: MenuAction,
    val ownerId: Long,
    val page: Int = 0,
    val live: Boolean = false,
    val channel: String? = null
) {
    fun serialize(): String =
        listOf(action.code, ownerId, page, if (live) 1 else 0, channel ?: NO_CHANNEL).joinToString(" ")

    companion object {
        private const val NO_CHANNEL = "-"
        private val WHITESPACE = "\\s+".toRegex()

        fun parse(data: String): MenuCallback? {
            val parts = data.trim().split(WHITESPACE)
            val action = parts.getOrNull(0)?.let { MenuAction.fromCode(it) } ?: return null
            val ownerId = parts.getOrNull(1)?.toLongOrNull() ?: return null
            val page = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val live = parts.getOrNull(3) == "1"
            val channel = parts.getOrNull(4)?.takeIf { it != NO_CHANNEL }
            return MenuCallback(action, ownerId, page, live, channel)
        }
    }
}
