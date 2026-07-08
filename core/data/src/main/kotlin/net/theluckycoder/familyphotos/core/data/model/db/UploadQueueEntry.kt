package net.theluckycoder.familyphotos.core.data.model.db

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import net.theluckycoder.familyphotos.core.data.model.UploadChoice

@Entity(tableName = "upload_queue", indices = [Index(value = ["localPhotoId"], unique = true)])
data class UploadQueueEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val localPhotoId: Long,
    val makePublic: Boolean?,
    val folderId: Long? = null,
    val newFolderName: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val isManualUpload: Boolean = false,
) {
    @Ignore
    fun toUploadChoice(): UploadChoice = when {
        folderId != null -> UploadChoice.Folder(folderId)
        newFolderName != null -> UploadChoice.NewFolder(newFolderName, makePublic!!)
        else -> UploadChoice.NoFolder(makePublic!!)
    }
}
