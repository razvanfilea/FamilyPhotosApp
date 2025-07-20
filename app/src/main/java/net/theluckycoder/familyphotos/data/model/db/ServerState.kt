package net.theluckycoder.familyphotos.data.model.db

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "server_state",
)
data class ServerState(
    @PrimaryKey
    val id: Int,
    val eventLogId: Long
)