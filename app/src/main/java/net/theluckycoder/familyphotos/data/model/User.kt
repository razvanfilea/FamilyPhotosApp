package net.theluckycoder.familyphotos.data.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Keep
@Serializable
data class User(
    val userId: String,
    val displayName: String,
)

const val PUBLIC_USER_ID = "public"

