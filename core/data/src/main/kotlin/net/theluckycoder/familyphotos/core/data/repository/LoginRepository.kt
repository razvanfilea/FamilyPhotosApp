package net.theluckycoder.familyphotos.core.data.repository

import android.util.Log
import dagger.Lazy
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import net.theluckycoder.familyphotos.core.data.model.network.UserLoginDto
import net.theluckycoder.familyphotos.core.data.remote.UserService
import javax.inject.Inject

@ViewModelScoped
class LoginRepository @Inject internal constructor(
    private val userDataStore: UserDataStore,
    private val userService: Lazy<UserService>,
) {
    val isLoggedIn =
        userDataStore.sessionCookie.combine(userDataStore.user) { sessionCookie, userName ->
            sessionCookie != null && userName != null
        }

    suspend fun login(userLogin: UserLoginDto) {
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
    suspend fun setBenchmarkCredentials(sessionCookie: String, username: String) {
        userDataStore.setSessionCookie(sessionCookie)
        userDataStore.setUser(UserDto(username, ""))
    }
}
