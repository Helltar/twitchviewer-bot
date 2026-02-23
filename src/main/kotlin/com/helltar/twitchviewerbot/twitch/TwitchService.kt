package com.helltar.twitchviewerbot.twitch

interface TwitchService {

    fun fetchActiveStreams(userLogins: List<String>): List<BroadcastData>
}
