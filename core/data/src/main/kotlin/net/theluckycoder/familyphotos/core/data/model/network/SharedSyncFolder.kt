package net.theluckycoder.familyphotos.core.data.model.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.theluckycoder.familyphotos.core.data.model.db.NetworkPhoto

@Serializable
data class SharedSyncFolder(
    @SerialName("id") val id: Long,
    @SerialName("owner_id") val ownerId: String?,
    @SerialName("name") val name: String,
    @SerialName("latest_event_id") val latestEventId: Long? = null,
    @SerialName("events") val events: List<PhotoEventLog>? = null,
    @SerialName("photos") val photos: List<NetworkPhoto>? = null,
)