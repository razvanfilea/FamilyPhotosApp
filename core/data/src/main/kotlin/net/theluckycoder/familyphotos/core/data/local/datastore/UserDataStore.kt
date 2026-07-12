package net.theluckycoder.familyphotos.core.data.local.datastore

import android.content.Context
import android.util.Log.i
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.theluckycoder.familyphotos.core.data.di.DefaultCoroutineScope
import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataStore @Inject constructor(
    @ApplicationContext context: Context,
    scope: DefaultCoroutineScope,
) {

    private val userDataStore = context.userDataStore

    internal val sessionCookie: Flow<String?> =
        userDataStore.data.map { it[SESSION_COOKIE] }.distinctUntilChanged()

    internal suspend fun setSessionCookie(value: String) = userDataStore.edit { preferences ->
        preferences[SESSION_COOKIE] = value
    }

    val serverAddress: Flow<String?> =
        userDataStore.data.map { it[SERVER_ADDRESS] }.distinctUntilChanged()

    suspend fun setServerAddress(value: String) = userDataStore.edit { preferences ->
        preferences[SERVER_ADDRESS] = value
    }

    val user: StateFlow<UserDto?> = userDataStore.data
        .map { UserDto(it[USER_ID] ?: return@map null, it[DISPLAY_NAME] ?: return@map null) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun setUser(value: UserDto) = userDataStore.edit { preferences ->
        preferences[USER_ID] = value.userId
        preferences[DISPLAY_NAME] = value.displayName
    }

    suspend fun clear() = userDataStore.edit { preferences ->
        preferences.clear()
    }

    private companion object {
        private val Context.userDataStore by preferencesDataStore("user_prefs")

        private val SESSION_COOKIE = stringPreferencesKey("session_cookie")
        private val USER_ID = stringPreferencesKey("user")
        private val DISPLAY_NAME = stringPreferencesKey("display_name")
        private val SERVER_ADDRESS = stringPreferencesKey("server_address")
    }
}
