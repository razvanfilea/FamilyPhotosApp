package net.theluckycoder.familyphotos.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateShareRequest(
    val folder_id: Long,
    val grantee_id: String,
    val can_upload: Boolean = false,
    val can_delete: Boolean = false,
    val expires_at: Long? = null,
)