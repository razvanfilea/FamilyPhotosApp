package net.theluckycoder.familyphotos.data.repository

import android.util.Log
import dagger.Lazy
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.data.model.UserLogin
import net.theluckycoder.familyphotos.data.remote.UserService
import javax.inject.Inject

@ViewModelScoped
class LoginRepository @Inject constructor(
    private val userDataStore: UserDataStore,
    private val userService: Lazy<UserService>,
) {
    val isLoggedIn =
        userDataStore.sessionCookie.combine(userDataStore.userIdFlow) { sessionCookie, userName ->
            sessionCookie != null && userName != null
        }

    suspend fun login(userLogin: UserLogin) {
        try {
            val user = userService.get().login(userLogin.userId, userLogin.password).body()
            user?.let {
                Log.v("LoginViewModel", "User logged in: $it")
                with(userDataStore) {
                    setUserName(it.userId)
                    setDisplayName(it.displayName)
                }
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
}
