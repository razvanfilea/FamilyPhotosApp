package net.theluckycoder.familyphotos.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    val firstStart: Flow<Boolean> =
        userDataStore.data.map { it[FIRST_START] ?: true }

    suspend fun setFirstStart() = userDataStore.edit { preferences ->
        preferences[FIRST_START] = false
    }

    val credentials: Flow<String?> =
        userDataStore.data.map { it[CREDENTIALS] }.distinctUntilChanged()

    suspend fun setCredentials(value: String) = userDataStore.edit { preferences ->
        preferences[CREDENTIALS] = value
    }

    val userIdFlow: Flow<Int> =
        userDataStore.data.map { it[USER_ID] ?: -1 }.distinctUntilChanged()

    suspend fun setUserId(value: Int) = userDataStore.edit { preferences ->
        preferences[USER_ID] = value
    }

    val displayNameFlow: Flow<String> =
        userDataStore.data.map { it[DISPLAY_NAME] ?: "" }.distinctUntilChanged()

    suspend fun setDisplayName(value: String) = userDataStore.edit { preferences ->
        preferences[DISPLAY_NAME] = value
    }

    private companion object {
        private val Context.userDataStore by preferencesDataStore("user_prefs")

        private const val KEY_CREDENTIALS = "credentials"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"

        private val FIRST_START = booleanPreferencesKey("first_start")
        private val CREDENTIALS = stringPreferencesKey(KEY_CREDENTIALS)
        private val USER_ID = intPreferencesKey(KEY_USER_ID)
        private val DISPLAY_NAME = stringPreferencesKey(KEY_DISPLAY_NAME)
    }
}
