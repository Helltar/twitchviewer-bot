package com.helltar.twitchviewerbot.media

enum class ClipFormat(val streamlinkQuality: String, val fileExtension: String) {
    VIDEO("720p,720p60,best", "mp4"),
    AUDIO("audio_only", "m4a")
}
