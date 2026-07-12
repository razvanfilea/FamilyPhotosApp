package net.theluckycoder.familyphotos.core.data.repository

import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import net.theluckycoder.familyphotos.core.data.model.network.UserLoginDto
import net.theluckycoder.familyphotos.core.data.remote.UserService
import javax.inject.Inject

class LoginRepository @Inject internal constructor(
    private val userDataStore: UserDataStore,
    private val userService: Lazy<UserService>,
) {
    val isLoggedIn =
        userDataStore.sessionCookie.combine(userDataStore.user) { sessionCookie, userName ->
            sessionCookie != null && userName != null
        }

    suspend fun login(serverAddress: String, userLogin: UserLoginDto) {
        val normalized = normalizeUrl(serverAddress)
        userDataStore.setServerAddress(normalized)
        try {
            val user = userService.get().login(userLogin.userId, userLogin.password).body()
            user?.let {
                Log.v("LoginViewModel", "User logged in: $it")
                userDataStore.setUser(user)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun logout() = coroutineScope {
        launch {
            try {
                userService.get().logout()
            } catch (_: Exception) {
            }
        }
        userDataStore.clear()
    }

    /**
     * Set credentials directly for benchmark tests, bypassing the login API.
     */
    suspend fun setBenchmarkCredentials(sessionCookie: String, username: String, serverAddress: String) {
        val normalized = normalizeUrl(serverAddress)
        userDataStore.setServerAddress(normalized)
        userDataStore.setSessionCookie(sessionCookie)
        userDataStore.setUser(UserDto(username, ""))
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.isEmpty()) {
            ""
        } else if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }
}
