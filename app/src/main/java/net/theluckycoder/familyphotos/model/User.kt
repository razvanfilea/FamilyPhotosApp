package net.theluckycoder.familyphotos.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Keep
@Serializable
data class User(
    val id: Int,
    val displayName: String,
    val userName: String,
)

const val PUBLIC_USER_ID = 1L

