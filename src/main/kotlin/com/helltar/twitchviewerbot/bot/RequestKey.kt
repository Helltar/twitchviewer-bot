package com.helltar.twitchviewerbot.bot

import java.util.concurrent.atomic.AtomicLong

object RequestKey {
    const val CLIP = "clip"
    const val SCREENSHOT = "screenshot"
    const val MENU = "menu"

    private val sequence = AtomicLong()

    fun forUser(name: String, userId: Long) = "$name@$userId"

    /** A name no other request shares, for requests that may run side by side: each clip request takes its own key. */
    fun unique(name: String) = "$name#${sequence.incrementAndGet()}"
}
