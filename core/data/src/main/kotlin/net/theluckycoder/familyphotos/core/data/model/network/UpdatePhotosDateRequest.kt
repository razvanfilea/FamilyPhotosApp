package net.theluckycoder.familyphotos.core.data.model.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class UpdatePhotosDateRequest(
    @SerialName("photo_ids")
    val photoIds: List<Long>,
    @SerialName("created_at")
    val createdAt: Long,
)
