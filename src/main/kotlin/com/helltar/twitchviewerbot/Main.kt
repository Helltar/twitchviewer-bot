package com.helltar.twitchviewerbot

import com.annimon.tgbotsmodule.Runner
import com.helltar.twitchviewerbot.bot.BotDependencies
import com.helltar.twitchviewerbot.bot.TwitchViewerBot
import com.helltar.twitchviewerbot.bot.toBotSettings
import com.helltar.twitchviewerbot.database.Database
import com.helltar.twitchviewerbot.health.Heartbeat
import com.helltar.twitchviewerbot.media.ClipTempStorage
import com.helltar.twitchviewerbot.twitch.TwitchService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

// registration talks to Telegram, so this leaves room for a slow network right after a reboot;
// once it succeeds the loop marks its first cycle within milliseconds
private val POLLING_START_TIMEOUT = 30.seconds

fun main(args: Array<String>) {
    Database.init(Config.database)

    ClipTempStorage.init()

    val telegram = Config.telegram

    val dependencies =
        BotDependencies(
            settings = telegram.toBotSettings(),
            twitchService = TwitchService(Config.twitch)
        )

    val heartbeat = Heartbeat()
    heartbeat.start()

    Runner.run(args.firstOrNull().orEmpty(), listOf(TwitchViewerBot(telegram.token, dependencies, heartbeat)))

    // the runner only logs a failed registration and returns as if it had worked, so a bot that never
    // started polling would sit here alive and idle. exiting hands the retry to the container runtime.
    if (!heartbeat.awaitFirstPoll(POLLING_START_TIMEOUT)) {
        log.error {
            "Long polling did not start within ${POLLING_START_TIMEOUT.inWholeSeconds}s — " +
                    "exiting so the container is restarted"
        }

        exitProcess(1)
    }
}
