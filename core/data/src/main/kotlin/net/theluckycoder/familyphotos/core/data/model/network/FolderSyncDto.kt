package net.theluckycoder.familyphotos.core.data.model.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class FolderSyncDto(
    @SerialName("id") val id: Long,
    @SerialName("owner_id") val ownerId: String?,
    @SerialName("name") val name: String,
    @SerialName("latest_event_id") val latestEventId: Long? = null,
    @SerialName("events") val events: List<PhotoEventLog>? = null,
    @SerialName("photos") val photos: List<NetworkPhotoDto>? = null,
)