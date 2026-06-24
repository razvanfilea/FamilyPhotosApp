package net.theluckycoder.familyphotos.core.data.model.db

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.theluckycoder.familyphotos.core.data.di.NetworkModule
import net.theluckycoder.familyphotos.core.data.utils.UriAsStringSerializer

@Immutable
@Serializable
sealed class Photo : Parcelable {

    abstract val id: Long
    abstract val name: String
    abstract val timeCreated: Long

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
    val folder: String?,
    @Serializable(UriAsStringSerializer::class)
    val uri: Uri,
    val mimeType: String? = null,
) : Photo(), Parcelable {

    val isSavedToCloud: Boolean
        @Ignore
        get() = networkPhotoId != 0L
}

@Immutable
@Keep
@Parcelize
@Serializable
@Entity(
    tableName = "network_photo",
    indices = [
        Index(value = ["timeCreated"], orders = [Index.Order.DESC]),
        Index(value = ["folderId"])
    ]
)
data class NetworkPhoto(
    @PrimaryKey
    override val id: Long,
    @SerialName("user_id")
    val userId: String?,
    override val name: String,
    @SerialName("created_at")
    override val timeCreated: Long,
    @SerialName("file_size")
    val fileSize: Long = 0,
    @SerialName("folder_id")
    val folderId: Long? = null,
    @SerialName("trashed_on")
    val trashedOn: Long? = null,
    @SerialName("thumb_hash")
    val thumbHash: String? = null,
) : Photo(), Parcelable

val Photo.isVideo
    get() = when (this) {
        is NetworkPhoto -> name.substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS
        is LocalPhoto -> mimeType?.startsWith("video/") == true
    }

fun Photo.getUri(): Uri = when (this) {
    is NetworkPhoto -> "${NetworkModule.BASE_URL}photos/download/$id".toUri()
    is LocalPhoto -> uri
}

fun Photo.getPreviewUri(): Uri = when (this) {
    is NetworkPhoto -> "${NetworkModule.BASE_URL}photos/preview/$id".toUri()
    is LocalPhoto -> uri
}

val Photo.thumbHash
    get() = when (this) {
        is NetworkPhoto -> thumbHash
        is LocalPhoto -> null
    }

val NetworkPhoto.isPublic
    get() = this.userId == null

// https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types/Common_types
private val VIDEO_EXTENSIONS = setOf(
    "3gp", "3g2", "h261", "h263", "h264", "jpgv", "jpgm", "jpm",
    "mj2", "mjp2", "ts", "mp4", "mp4v", "mpg4", "m1v", "m2v",
    "mpa", "mpe", "mpeg", "mpg", "ogv", "mov", "qt", "fvt",
    "m4u", "mxu", "pyv", "viv", "webm", "f4v", "fli", "flv",
    "m4v", "mkv", "asf", "asx", "wm", "wmv", "wmx", "wvx",
    "avi", "movie",
)