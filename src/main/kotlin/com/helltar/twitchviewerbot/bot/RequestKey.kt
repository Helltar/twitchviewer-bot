package com.helltar.twitchviewerbot.bot

object RequestKey {
    const val CLIP = "clip"
    const val SCREENSHOT = "screenshot"
    const val MENU = "menu"
    fun forUser(name: String, userId: Long) = "$name@$userId"
}
