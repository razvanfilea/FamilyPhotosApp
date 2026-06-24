package net.theluckycoder.familyphotos.core.data.model.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateShareRequest(
    @SerialName("folder_id") val folderId: Long,
    @SerialName("grantee_id") val granteeId: String,
    @SerialName("can_upload") val canUpload: Boolean = false,
    @SerialName("can_delete") val canDelete: Boolean = false,
    @SerialName("expires_at") val expiresAt: Long? = null,
)