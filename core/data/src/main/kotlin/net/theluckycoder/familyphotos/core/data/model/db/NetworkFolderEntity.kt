package net.theluckycoder.familyphotos.core.data.model.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_folder")
data class NetworkFolderEntity(
    @PrimaryKey val id: Long,
    val ownerId: String?,
    val name: String,
    val createdAt: Long,
    val latestEventId: Long = 0,
    val canUpload: Boolean = true,
    val canDelete: Boolean = true,
)

val NetworkFolderEntity.isPublic
    get() = this.ownerId == null
