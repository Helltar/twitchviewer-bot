package com.helltar.twitchviewerbot.database.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.javatime.timestamp
import java.time.Instant

object UserChannelsTable : IntIdTable() {

    val userId = reference("user_id", UsersTable.userId, onDelete = ReferenceOption.CASCADE)
    val channelName = varchar("channel_name", 50) // todo: ChannelsTable
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    init {
        uniqueIndex(userId, channelName)
    }
}
