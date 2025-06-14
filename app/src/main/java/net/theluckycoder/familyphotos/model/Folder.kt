package net.theluckycoder.familyphotos.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import kotlinx.serialization.Serializable

abstract class PhotoFolder {
    abstract val name: String
    abstract val coverPhotoId: Long
    abstract val count: Int
}

@Immutable
@Serializable
data class NetworkFolder(
    @ColumnInfo(name = "folder")
    override val name: String,
    @ColumnInfo(name = "id")
    override val coverPhotoId: Long,
    @ColumnInfo(name = "userId")
    val userId: String,
    @ColumnInfo(name = "photoCount")
    override val count: Int,
) : PhotoFolder() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NetworkFolder

        if (name != other.name) return false
        if (userId != other.userId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + userId.hashCode()
        return result
    }
}

fun NetworkFolder.isPublic() = this.userId == PUBLIC_USER_ID

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
) : PhotoFolder()
