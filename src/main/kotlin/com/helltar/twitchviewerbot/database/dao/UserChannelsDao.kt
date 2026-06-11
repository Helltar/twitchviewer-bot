package com.helltar.twitchviewerbot.database.dao

import com.helltar.twitchviewerbot.database.Database.dbTransaction
import com.helltar.twitchviewerbot.database.tables.UserChannelsTable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertIgnore
import org.jetbrains.exposed.v1.r2dbc.select

class UserChannelsDao {

    suspend fun add(userId: Long, channelName: String): Boolean = dbTransaction {
        UserChannelsTable
            .insertIgnore {
                it[this.userId] = userId
                it[this.channelName] = channelName
            }
            .insertedCount > 0
    }

    suspend fun delete(userId: Long, channelName: String): Boolean = dbTransaction {
        UserChannelsTable
            .deleteWhere {
                UserChannelsTable.userId eq userId and
                        (UserChannelsTable.channelName eq channelName)
            } > 0
    }

    suspend fun list(userId: Long): List<String> = dbTransaction {
        UserChannelsTable
            .select(UserChannelsTable.channelName)
            .where { UserChannelsTable.userId eq userId }
            .map { it[UserChannelsTable.channelName] }
            .toList()
    }

    suspend fun hasChannels(userId: Long): Boolean = dbTransaction {
        UserChannelsTable
            .select(UserChannelsTable.userId)
            .where { UserChannelsTable.userId eq userId }
            .empty()
            .not()
    }
}

val userChannelsDao = UserChannelsDao()
