package net.theluckycoder.familyphotos.core.data.model.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto

@Serializable
internal data class NetworkPhotoDto(
    @SerialName("id")
    val id: Long,
    @SerialName("user_id")
    val userId: String?,
    @SerialName("name")
    val name: String,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("file_size")
    val fileSize: Long = 0,
    @SerialName("folder_id")
    val folderId: Long? = null,
    @SerialName("trashed_on")
    val trashedOn: Long? = null,
    @SerialName("thumb_hash")
    val thumbHash: String? = null,
)

internal fun NetworkPhotoDto.toEntity() = NetworkPhoto(
    id = id,
    userId = userId,
    name = name,
    timeCreated = createdAt,
    fileSize = fileSize,
    folderId = folderId,
    trashedOn = trashedOn,
    thumbHash = thumbHash
)

