package com.helltar.twitchviewerbot.database

import com.helltar.twitchviewerbot.DatabaseConfig
import com.helltar.twitchviewerbot.database.tables.UserChannelsTable
import com.helltar.twitchviewerbot.database.tables.UsersTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.time.Instant

object Database {

    fun init(config: DatabaseConfig) {
        val url = "r2dbc:postgresql://${config.host}:${config.port}/${config.name}"
        val database = R2dbcDatabase.connect(url, user = config.user, password = config.password)

        runBlocking {
            suspendTransaction(database) {
                SchemaUtils.create(UsersTable, UserChannelsTable)
            }
        }
    }

    fun now(): Instant =
        Instant.now()

    suspend fun <T> dbTransaction(block: suspend R2dbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) {
            suspendTransaction { block() }
        }
}
