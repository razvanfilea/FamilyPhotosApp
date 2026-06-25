package net.theluckycoder.familyphotos.core.data.model.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateShareRequest(
    @SerialName("can_upload") val canUpload: Boolean,
    @SerialName("can_delete") val canDelete: Boolean,
)
