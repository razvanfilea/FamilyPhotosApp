package net.theluckycoder.familyphotos.repository

import dagger.Lazy
import dagger.hilt.android.scopes.ViewModelScoped
import net.theluckycoder.familyphotos.model.User
import net.theluckycoder.familyphotos.model.UserLogin
import net.theluckycoder.familyphotos.network.service.UserService
import javax.inject.Inject

@ViewModelScoped
class LoginRepository @Inject constructor(
    private val userService: Lazy<UserService>,
) {

    suspend fun login(userLogin: UserLogin): User? {
        return userService.get().getUser("Basic " + userLogin.encodeBase64(), userLogin.userName).body()
    }
}
