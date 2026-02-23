package com.helltar.twitchviewerbot.twitch

data class BroadcastData(
    val login: String,
    val username: String,
    val title: String,
    val viewerCount: Int,
    val gameName: String,
    val thumbnailUrl: String,
    val startedAt: String,
    val uptime: String
)
