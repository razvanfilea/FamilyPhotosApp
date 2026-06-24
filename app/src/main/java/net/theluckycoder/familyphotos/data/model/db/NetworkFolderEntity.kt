package net.theluckycoder.familyphotos.data.model.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "network_folder")
@Serializable
data class NetworkFolderEntity(
    @PrimaryKey val id: Long,
    @SerialName("owner_id") val ownerId: String?,
    @SerialName("name") val name: String,
    @SerialName("latest_event_id") val latestEventId: Long = 0,
    @SerialName("created_at") val createdAt: Long,
)