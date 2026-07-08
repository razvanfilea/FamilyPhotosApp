package net.theluckycoder.familyphotos.core.data.model.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateFolderRequest(
    @SerialName("name")
    val name: String,
    @SerialName("is_public")
    val isPublic: Boolean
)