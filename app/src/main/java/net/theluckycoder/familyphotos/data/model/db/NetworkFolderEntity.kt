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
    val name: String,
    @SerialName("created_at") val createdAt: Long,
)