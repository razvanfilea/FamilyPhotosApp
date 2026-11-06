package net.theluckycoder.familyphotos.core.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A file shared from another app, copied into app-private storage so it can be uploaded after
 * the share's URI grant has expired. A row exists only while the file is queued.
 *
 * @param fileName name of the copy inside the shared uploads directory
 * @param displayName name the file is uploaded with
 * @param timeCreated capture time in epoch seconds
 */
@Immutable
@Serializable
@Entity(tableName = "shared_upload")
data class SharedUploadFile(
    @PrimaryKey
    val fileName: String,
    val displayName: String,
    val timeCreated: Long,
    val mimeType: String,
)
