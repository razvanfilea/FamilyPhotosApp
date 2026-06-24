package net.theluckycoder.familyphotos.core.data.model.network

import android.util.Base64
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable

@Immutable
@Keep
data class UserLoginDto(
    val userId: String,
    val password: String,
) {

    fun encodeBase64(): String =
        Base64.encodeToString("$userId:$password".encodeToByteArray(), Base64.NO_WRAP)
}