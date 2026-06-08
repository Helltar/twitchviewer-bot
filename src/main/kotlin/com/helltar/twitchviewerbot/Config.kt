package com.helltar.twitchviewerbot

import io.github.cdimascio.dotenv.dotenv

data class TelegramConfig(
    val creatorId: Long,
    val token: String,
    val username: String
)

data class TwitchConfig(
    val clientId: String,
    val clientSecret: String
)

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val name: String,
    val user: String,
    val password: String
)

object Config {

    private val dotenv = dotenv { ignoreIfMissing = true }

    val telegram =
        TelegramConfig(
            creatorId = readNumericEnv("CREATOR_ID", String::toLongOrNull),
            token = readEnv("BOT_TOKEN"),
            username = readEnv("BOT_USERNAME")
        )

    val twitch =
        TwitchConfig(
            clientId = readEnv("TWITCH_CLIENT_ID"),
            clientSecret = readEnv("TWITCH_CLIENT_SECRET")
        )

    val database =
        DatabaseConfig(
            host = readEnv("POSTGRESQL_HOST"),
            port = readNumericEnv("POSTGRESQL_PORT", String::toIntOrNull),
            name = readEnv("DATABASE_NAME"),
            user = readEnv("DATABASE_USER"),
            password = readEnv("DATABASE_PASSWORD")
        )

    private fun readEnv(env: String) =
        dotenv[env]?.ifBlank { throw IllegalArgumentException("$env must not be empty") }
            ?: throw IllegalArgumentException("$env environment variable is not set")

    private fun <T : Number> readNumericEnv(env: String, parser: String.() -> T?) =
        readEnv(env).let { value ->
            value.parser() ?: throw IllegalArgumentException("$env must be numeric, but was: $value")
        }
}
