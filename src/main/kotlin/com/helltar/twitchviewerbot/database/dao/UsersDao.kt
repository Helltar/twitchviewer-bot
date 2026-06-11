package com.helltar.twitchviewerbot.database.dao

import com.helltar.twitchviewerbot.database.Database.dbTransaction
import com.helltar.twitchviewerbot.database.tables.UsersTable
import com.helltar.twitchviewerbot.database.tables.UsersTable.clipDuration
import com.helltar.twitchviewerbot.database.tables.UsersTable.firstName
import com.helltar.twitchviewerbot.database.tables.UsersTable.languageCode
import com.helltar.twitchviewerbot.database.tables.UsersTable.updatedAt
import com.helltar.twitchviewerbot.database.tables.UsersTable.username
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.upsert
import org.telegram.telegrambots.meta.api.objects.User
import java.time.Instant

class UsersDao {

    suspend fun upsert(user: User) = dbTransaction {
        UsersTable
            .upsert(
                onUpdate = {
                    it[firstName] = user.firstName
                    it[username] = user.userName
                    it[languageCode] = user.languageCode
                    it[updatedAt] = Instant.now()
                })
            {
                it[userId] = user.id
                it[firstName] = user.firstName
                it[username] = user.userName
                it[languageCode] = user.languageCode
            }
    }

    suspend fun languageCode(userId: Long): String? = dbTransaction {
        UsersTable
            .select(languageCode)
            .where { UsersTable.userId eq userId }
            .singleOrNull()?.get(languageCode)
    }

    suspend fun clipDuration(userId: Long): Int = dbTransaction {
        UsersTable
            .select(clipDuration)
            .where { UsersTable.userId eq userId }
            .singleOrNull()?.get(clipDuration) ?: 30
    }

    suspend fun setClipDuration(userId: Long, duration: Int) = dbTransaction {
        UsersTable.update({ UsersTable.userId eq userId }) {
            it[clipDuration] = duration
            it[updatedAt] = Instant.now()
        }
    }
}

val usersDao = UsersDao()
