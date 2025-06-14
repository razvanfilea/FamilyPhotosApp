package net.theluckycoder.familyphotos.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataStore @Inject constructor(@ApplicationContext context: Context) {

    private val userDataStore = context.userDataStore

    val sessionCookie: Flow<String?> =
        userDataStore.data.map { it[SESSION_COOKIE] }.distinctUntilChanged()

    suspend fun setSessionCookie(value: String) = userDataStore.edit { preferences ->
        preferences[SESSION_COOKIE] = value
    }

    val userIdFlow: Flow<String?> =
        userDataStore.data.map { it[USER_ID] }.distinctUntilChanged()

    suspend fun setUserName(value: String) = userDataStore.edit { preferences ->
        preferences[USER_ID] = value
    }

    val displayNameFlow: Flow<String> =
        userDataStore.data.map { it[DISPLAY_NAME] ?: "" }.distinctUntilChanged()

    suspend fun setDisplayName(value: String) = userDataStore.edit { preferences ->
        preferences[DISPLAY_NAME] = value
    }

    suspend fun clear() = userDataStore.edit { preferences ->
        preferences.clear()
    }

    private companion object {
        private val Context.userDataStore by preferencesDataStore("user_prefs")

        private val SESSION_COOKIE = stringPreferencesKey("session_cookie")
        private val USER_ID = stringPreferencesKey("user")
        private val DISPLAY_NAME = stringPreferencesKey("display_name")
    }
}
