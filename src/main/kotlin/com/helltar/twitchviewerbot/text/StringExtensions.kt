package com.helltar.twitchviewerbot.text

import java.util.UUID

fun String.toTwitchHtmlLink(username: String) =
    """<b><a href="https://www.twitch.tv/$this">$username</a></b>"""

fun String.escapeHtml() =
    this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#039;")

fun String.toHashTag() =
    this.replace("""[^\p{L}\p{Z}\d]""".toRegex(), "").replace("""\s""".toRegex(), "_")

fun String.plusUUID() =
    "${this}_${UUID.randomUUID()}"
