package net.theluckycoder.familyphotos.core.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo

abstract class PhotoFolder {
    abstract val name: String
    abstract val coverPhotoId: Long
    abstract val count: Int

    abstract fun getCoverPhoto(): Photo
}

@Immutable
data class NetworkFolder(
    @ColumnInfo(name = "folderId")
    val id: Long,
    @ColumnInfo(name = "folderName")
    override val name: String,
    @ColumnInfo(name = "coverPhotoId")
    override val coverPhotoId: Long,
    @ColumnInfo(name = "userId")
    val userId: String?,
    @ColumnInfo(name = "photoCount")
    override val count: Int,
) : PhotoFolder() {

    override fun getCoverPhoto(): Photo = NetworkPhoto(
        id = coverPhotoId,
        name = "",
        userId = userId,
        timeCreated = 0L,
        folderId = id,
    )
}

val NetworkFolder.isPublic
    get() = this.userId == null

@Immutable
data class LocalFolder(
    @ColumnInfo(name = "folder")
    override val name: String,
    @ColumnInfo(name = "id")
    override val coverPhotoId: Long,
    @ColumnInfo(name = "uri")
    val coverPhotoUri: Uri,
    @ColumnInfo(name = "COUNT(id)")
    override val count: Int,
) : PhotoFolder() {
    override fun getCoverPhoto(): Photo = LocalPhoto(
        id = coverPhotoId,
        name = "",
        uri = coverPhotoUri,
        timeCreated = 0L,
        folder = name
    )
}
