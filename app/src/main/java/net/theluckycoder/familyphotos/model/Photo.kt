package net.theluckycoder.familyphotos.model

import android.net.Uri
import android.os.Parcelable
import android.webkit.MimeTypeMap
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.theluckycoder.familyphotos.network.NetworkModule
import androidx.core.net.toUri

@Serializable
sealed class Photo : Parcelable {

    abstract val id: Long
    abstract val name: String
    abstract val timeCreated: Long
    abstract val folder: String?

    override fun hashCode(): Int {
        throw NotImplementedError("This should not be used directly")
    }

    override fun equals(other: Any?): Boolean {
        throw NotImplementedError("This should not be used directly")
    }
}

@Immutable
@Keep
@Serializable
@Parcelize
@Entity(tableName = "local_photo")
data class LocalPhoto(
    @PrimaryKey
    override val id: Long,
    val networkPhotoId: Long = 0L,
    override val name: String,
    override val timeCreated: Long,
    override val folder: String?,
    @Serializable(UriAsStringSerializer::class)
    val uri: Uri,
    val mimeType: String? = null,
) : Photo(), Parcelable {

    val isSavedToCloud: Boolean
        @Ignore
        get() = networkPhotoId != 0L
}

@Serializable
@Keep
data class BasicNetworkPhoto(
    val id: Long,
    val userId: String,
    val name: String,
    val createdAt: Long,
    val fileSize: Long,
    val folder: String?,
)

@Immutable
@Keep
@Serializable
@Parcelize
@Entity(
    tableName = "network_photo",
    indices = [
        Index(value = ["timeCreated"], orders = [Index.Order.DESC])
    ]
)
data class NetworkPhoto(
    @PrimaryKey
    override val id: Long,
    val userId: String,
    override val name: String,
    @SerialName("createdAt")
    override val timeCreated: Long,
    val fileSize: Long = 0,
    override val folder: String? = null,
    val isFavorite: Boolean = false,
) : Photo(), Parcelable

val Photo.isVideo
    get() = when (this) {
        is NetworkPhoto -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            name.substringAfterLast('.')
        )
        is LocalPhoto -> mimeType
    }?.startsWith("video/") == true

fun Photo.getUri(): Uri = when (this) {
    is NetworkPhoto -> getDownloadUrl().toUri()
    is LocalPhoto -> uri
}

fun Photo.getPreviewUri(): Uri = when (this) {
    is NetworkPhoto -> getPreviewUrl().toUri()
    is LocalPhoto -> uri
}

fun NetworkPhoto.getDownloadUrl(): String =
    "${NetworkModule.BASE_URL}photos/download/$id"

fun NetworkPhoto.isPublic() = this.userId == PUBLIC_USER_ID

fun NetworkPhoto.getPreviewUrl(): String =
    "${NetworkModule.BASE_URL}photos/preview/$id"
