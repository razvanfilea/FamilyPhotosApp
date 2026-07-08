package net.theluckycoder.familyphotos.core.data.model.network

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class UserDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String,
)