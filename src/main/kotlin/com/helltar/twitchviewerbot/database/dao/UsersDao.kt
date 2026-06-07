package com.helltar.twitchviewerbot.database.dao

import com.helltar.twitchviewerbot.database.Database.dbTransaction
import com.helltar.twitchviewerbot.database.Database.now
import com.helltar.twitchviewerbot.database.tables.UsersTable
import com.helltar.twitchviewerbot.database.tables.UsersTable.firstName
import com.helltar.twitchviewerbot.database.tables.UsersTable.languageCode
import com.helltar.twitchviewerbot.database.tables.UsersTable.updatedAt
import com.helltar.twitchviewerbot.database.tables.UsersTable.username
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.upsert
import org.telegram.telegrambots.meta.api.objects.User

class UsersDao {

    suspend fun upsert(user: User) = dbTransaction {
        UsersTable
            .upsert(
                onUpdate = {
                    it[firstName] = user.firstName
                    it[username] = user.userName
                    it[languageCode] = user.languageCode
                    it[updatedAt] = now()
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
}

val usersDao = UsersDao()
