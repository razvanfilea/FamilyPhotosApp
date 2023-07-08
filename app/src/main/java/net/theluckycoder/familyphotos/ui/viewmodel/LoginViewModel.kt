package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.datastore.UserDataStore
import net.theluckycoder.familyphotos.model.UserLogin
import net.theluckycoder.familyphotos.repository.LoginRepository
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val userDataStore: UserDataStore,
) : ViewModel() {

    val isLoggedIn =
        userDataStore.sessionCookie.combine(userDataStore.userIdFlow) { sessionCookie, userName ->
            sessionCookie != null && userName != null
        }

    fun login(userLogin: UserLogin) = viewModelScope.launch(Dispatchers.IO) {
        try {
            loginRepository.login(userLogin)?.let {
                with(userDataStore) {
                    setUserName(it.userId)
                    setDisplayName(it.displayName)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
