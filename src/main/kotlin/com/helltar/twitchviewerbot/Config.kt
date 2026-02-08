package com.helltar.twitchviewerbot

import io.github.cdimascio.dotenv.dotenv

object Config {

    private val dotenv = dotenv { ignoreIfMissing = true }

    val creatorId = readEnv("CREATOR_ID").toLongOrNull() ?: throw IllegalArgumentException("bad CREATOR_ID env")
    val botToken = readEnv("BOT_TOKEN")
    val botUsername = readEnv("BOT_USERNAME")

    val twitchClientId = readEnv("TWITCH_CLIENT_ID")
    val twitchClientSecret = readEnv("TWITCH_CLIENT_SECRET")

    val postgresqlHost = readEnv("POSTGRESQL_HOST")
    val postgresqlPort = readEnv("POSTGRESQL_PORT")
    val databaseName = readEnv("DATABASE_NAME")
    val databaseUser = readEnv("DATABASE_USER")
    val databasePassword = readEnv("DATABASE_PASSWORD")

    val javaTempDir = System.getProperty("java.io.tmpdir") ?: "/tmp"

    private fun readEnv(env: String) =
        dotenv[env] ?: throw IllegalArgumentException("error when read $env env")
}
