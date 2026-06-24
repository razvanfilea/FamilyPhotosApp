package net.theluckycoder.familyphotos.core.data.model.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SharedNetworkFolderDto(
    @SerialName("id") val id: Long,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("folder_name") val folderName: String,
    @SerialName("grantee_id") val granteeId: String,
    @SerialName("token") val token: String?,
    @SerialName("can_upload") val canUpload: Boolean,
    @SerialName("can_delete") val canDelete: Boolean,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("expires_at") val expiresAt: Long,
)

