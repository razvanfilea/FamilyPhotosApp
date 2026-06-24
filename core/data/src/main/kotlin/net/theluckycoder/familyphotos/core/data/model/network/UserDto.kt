package net.theluckycoder.familyphotos.core.data.model.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String,
)