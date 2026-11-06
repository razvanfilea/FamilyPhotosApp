package net.theluckycoder.familyphotos.core.data.model.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import net.theluckycoder.familyphotos.core.data.model.SharedUploadFile
import net.theluckycoder.familyphotos.core.data.model.UploadChoice

/**
 * Uploads either a MediaStore photo ([localPhotoId]) or a file shared from another app
 * ([sharedFileName]); exactly one of them is set.
 */
@Entity(
    tableName = "upload_queue",
    indices = [
        Index(value = ["localPhotoId"], unique = true),
        Index(value = ["sharedFileName"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = SharedUploadFile::class,
            parentColumns = ["fileName"],
            childColumns = ["sharedFileName"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class UploadQueueEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val localPhotoId: Long?,
    val makePublic: Boolean?,
    val folderId: Long? = null,
    val newFolderName: String? = null,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val isManualUpload: Boolean = false,
    val sharedFileName: String? = null,
) {
    @Ignore
    fun toUploadChoice(): UploadChoice = when {
        folderId != null -> UploadChoice.Folder(folderId)
        newFolderName != null -> UploadChoice.NewFolder(newFolderName, makePublic!!)
        else -> UploadChoice.NoFolder(makePublic!!)
    }

    companion object {
        fun forLocalPhoto(
            localPhotoId: Long,
            uploadChoice: UploadChoice,
            isManualUpload: Boolean,
        ) = create(uploadChoice, isManualUpload, localPhotoId = localPhotoId, sharedFileName = null)

        fun forSharedFile(file: SharedUploadFile, uploadChoice: UploadChoice) =
            create(uploadChoice, isManualUpload = true, localPhotoId = null, sharedFileName = file.fileName)

        private fun create(
            uploadChoice: UploadChoice,
            isManualUpload: Boolean,
            localPhotoId: Long?,
            sharedFileName: String?,
        ) = UploadQueueEntry(
            localPhotoId = localPhotoId,
            makePublic = uploadChoice.isPublic,
            folderId = (uploadChoice as? UploadChoice.Folder)?.folderId,
            newFolderName = (uploadChoice as? UploadChoice.NewFolder)?.name,
            isManualUpload = isManualUpload,
            sharedFileName = sharedFileName,
        )
    }
}
