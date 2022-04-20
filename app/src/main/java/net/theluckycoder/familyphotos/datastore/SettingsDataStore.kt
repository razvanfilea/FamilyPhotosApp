package net.theluckycoder.familyphotos.datastore

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext context: Context) {

    private val settingsDataStore = context.settingsDataStore

    fun dataStore() = settingsDataStore

    val cacheSizeMbFlow: Flow<Int> =
        settingsDataStore.data.map { it[CACHE_SIZE] ?: DEFAULT_CACHE_SIZE }.distinctUntilChanged()

    /*suspend fun setCacheSizeMb(value: Int) = settingsDataStore.edit { preferences ->
        preferences[CACHE_SIZE] = value
    }*/

    companion object {
        private val Context.settingsDataStore by preferencesDataStore("settings_prefs")

        val CACHE_SIZE = intPreferencesKey("cache_size")

        const val DEFAULT_CACHE_SIZE = 1024
    }
}
