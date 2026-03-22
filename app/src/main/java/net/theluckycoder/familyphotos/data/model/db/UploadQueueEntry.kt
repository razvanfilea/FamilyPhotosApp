package net.theluckycoder.familyphotos.data.model.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "upload_queue", indices = [Index(value = ["localPhotoId"], unique = true)])
data class UploadQueueEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val localPhotoId: Long,
    val makePublic: Boolean,
    val uploadFolder: String?,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
)
