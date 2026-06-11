package com.helltar.twitchviewerbot

import com.annimon.tgbotsmodule.services.ResourceBundleLocalizationService

object LocalizationKeys {

    const val ADD_COMMAND_INFO = "add_command_info"
    const val CHANNEL_ADDED_TO_LIST = "channel_added_to_list"
    const val CHANNEL_ALREADY_EXISTS_IN_LIST = "channel_already_exists_in_list"
    const val CLIP_COMMAND_INFO = "clip_command_info"
    const val DONT_TOUCH_IS_NOT_YOUR_LIST = "dont_touch_is_not_your_list"
    const val EMPTY_ONLINE_LIST = "empty_online_list"
    const val FILTER_NO_CHANNELS_FOUND = "filter_no_channels_found"
    const val GET_CLIP_FAIL = "get_clip_fail"
    const val INVALID_CHANNEL_NAME = "invalid_channel_name"
    const val INVALID_CHANNEL_NAME_LENGTH = "invalid_channel_name_length"
    const val LIST_FULL = "list_full"
    const val LIST_IS_EMPTY = "list_is_empty"
    const val SCREENSHOT_COMMAND_INFO = "screen_command_info"
    const val MANY_REQUEST = "many_request"
    const val NO_ACTIVE_TASKS = "no_active_tasks"
    const val START_COMMAND_INFO = "start_command_info"
    const val START_GET_CLIP = "start_get_clip"
    const val START_GET_AUDIO_CLIP = "start_get_audio_clip"
    const val STREAM_UPTIME = "stream_uptime"
    const val STREAM_VIEWERS = "stream_viewers"
    const val TASKS_ARE_CANCELLED = "tasks_are_cancelled"
    const val TWITCH_EXCEPTION = "twitch_exception"
    const val USER_CLOSE_LIST = "user_close_list"
    const val WAIT_CHECK_ONLINE = "wait_check_online"

    const val BTN_BACK = "btn_back"
    const val BTN_CLOSE_LIST = "btn_close_list"
    const val BTN_DELETE = "btn_delete"
    const val BTN_RECORD_ALL = "btn_record_all"
    const val BTN_SHORT_CLIP = "btn_short_clip"
    const val BTN_AUDIO_CLIP = "btn_audio_clip"
    const val BTN_CAPTURE_ALL = "btn_capture_all"
    const val BTN_SETTINGS = "btn_settings"
    const val TITLE_CHANNEL_IS_SELECTED = "title_channel_is_selected"
    const val TITLE_CHOOSE_CHANNEL_OR_ACTION = "title_choose_channel_or_action"
    const val TITLE_SETTINGS = "title_settings"

    private val localization = ResourceBundleLocalizationService("language")

    fun localizedString(key: String, languageCode: String?): String =
        localization.getString(key, languageCode ?: "en")
}
