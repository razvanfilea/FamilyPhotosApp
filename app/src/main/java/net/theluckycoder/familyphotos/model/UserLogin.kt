package net.theluckycoder.familyphotos.model

import android.util.Base64
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable

@Immutable
@Keep
data class UserLogin(
    val userName: String,
    val password: String,
) {

    fun encodeBase64(): String =
        Base64.encodeToString("$userName:$password".encodeToByteArray(), Base64.NO_WRAP)
}
