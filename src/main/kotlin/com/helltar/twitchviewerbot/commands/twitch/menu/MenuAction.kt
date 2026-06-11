package com.helltar.twitchviewerbot.commands.twitch.menu

enum class MenuAction(val code: String) {

    OPEN_CHANNEL("ch"),
    CLIP_ONE("c1"),
    CLIP_AUDIO_ONE("au"),
    CLIP_ALL("ca"),
    SCREENSHOT_ALL("sa"),
    DELETE_CHANNEL("rm"),
    OPEN_SETTINGS("st"),
    SET_DURATION("du"),
    BACK("bk"),
    PAGE("pg"),
    CLOSE("cl");

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String) = byCode[code]
    }
}
