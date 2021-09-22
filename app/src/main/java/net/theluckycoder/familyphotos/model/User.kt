package net.theluckycoder.familyphotos.model

import androidx.annotation.Keep

@Keep
data class User(
    val id: Int,
    val displayName: String,
    val userName: String,
)
