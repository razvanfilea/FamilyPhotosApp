package net.theluckycoder.familyphotos.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class User(
    val id: Int,
    val displayName: String,
    val userName: String,
)
