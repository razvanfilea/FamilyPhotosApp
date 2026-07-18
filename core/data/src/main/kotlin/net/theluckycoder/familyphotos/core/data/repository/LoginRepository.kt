package net.theluckycoder.familyphotos.core.data.repository

import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import net.theluckycoder.familyphotos.core.data.model.network.UserLoginDto
import net.theluckycoder.familyphotos.core.data.remote.UserService
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import javax.inject.Inject

sealed interface ConnectionTestResult {
    data object Success : ConnectionTestResult
    data object Unreachable : ConnectionTestResult
    data object InvalidAddress : ConnectionTestResult
}

sealed interface LoginResult {
    data object Success : LoginResult
    data object ServerUnreachable : LoginResult
    data object InvalidCredentials : LoginResult
}

class LoginRepository @Inject internal constructor(
    private val userDataStore: UserDataStore,
    private val userService: Lazy<UserService>,
) {
    val isLoggedIn =
        userDataStore.sessionCookie.combine(userDataStore.user) { sessionCookie, userName ->
            sessionCookie != null && userName != null
        }

    val serverAddress = userDataStore.serverAddress

    suspend fun testServerConnection(serverAddress: String): ConnectionTestResult {
        val normalized = normalizeUrl(serverAddress)
        if (normalized.isEmpty()) return ConnectionTestResult.InvalidAddress
        
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(5))
                    .build()
                val request = Request.Builder()
                    .url(normalized)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code in 200..499) {
                        ConnectionTestResult.Success
                    } else {
                        ConnectionTestResult.Unreachable
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginRepository", "Connection check failed for $normalized", e)
                ConnectionTestResult.Unreachable
            }
        }
    }

    suspend fun login(serverAddress: String, userLogin: UserLoginDto): LoginResult {
        val normalized = normalizeUrl(serverAddress)
        
        val testResult = testServerConnection(normalized)
        if (testResult !is ConnectionTestResult.Success) {
            return LoginResult.ServerUnreachable
        }

        userDataStore.setServerAddress(normalized)
        return try {
            val response = userService.get().login(userLogin.userId, userLogin.password)
            val user = response.body()
            if (response.isSuccessful && user != null) {
                Log.v("LoginViewModel", "User logged in: $user")
                userDataStore.setUser(user)
                LoginResult.Success
            } else {
                Log.w("LoginViewModel", "Login failed: ${response.code()} ${response.message()}")
                LoginResult.InvalidCredentials
            }
        } catch (e: Exception) {
            Log.e("LoginViewModel", "Login exception", e)
            LoginResult.InvalidCredentials
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
