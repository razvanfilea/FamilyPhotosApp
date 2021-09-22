package net.theluckycoder.familyphotos.model

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Ignore

data class NetworkFolder @JvmOverloads constructor(
    @ColumnInfo(name = "folder")
    val name: String,
    @ColumnInfo(name = "id")
    val coverPhotoId: Long,
    @ColumnInfo(name = "ownerUserId")
    val ownerUserId: Long,
    @ColumnInfo(name = "COUNT(id)")
    val count: Int,
    @Ignore
    val isPublic: Boolean = false
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NetworkFolder

        if (name != other.name) return false
        if (ownerUserId != other.ownerUserId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + ownerUserId.hashCode()
        return result
    }
}

class LocalFolder(
    @ColumnInfo(name = "folder")
    val name: String,
    @ColumnInfo(name = "id")
    val coverPhotoId: Long,
    @ColumnInfo(name = "uri")
    val coverPhotoUri: Uri,
    @ColumnInfo(name = "COUNT(id)")
    val count: Int,
)
