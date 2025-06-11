package net.theluckycoder.familyphotos.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.theluckycoder.familyphotos.model.PhotoType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext context: Context) {

    private val settingsDataStore = context.settingsDataStore

    fun dataStore() = settingsDataStore

    val cacheSizeMbFlow: Flow<Int> =
        settingsDataStore.data.map { it[CACHE_SIZE] ?: DEFAULT_CACHE_SIZE }.distinctUntilChanged()

    val showFoldersAscending: Flow<Boolean> =
        settingsDataStore.data.map { it[SHOW_FOLDERS_ASCENDING] != false }.distinctUntilChanged()

    val photoType: Flow<PhotoType> =
        settingsDataStore.data.map {
            when (it[FOLDERS_FILTER_TYPE]) {
                PhotoType.Personal.index -> PhotoType.Personal
                PhotoType.Family.index -> PhotoType.Family
                else -> PhotoType.All
            }
        }.distinctUntilChanged()

    val zoomLevel: Flow<Int?> =
        settingsDataStore.data.map { it[PHOTOS_ZOOM_LEVEL] }.distinctUntilChanged()

    suspend fun setShowFoldersAscending(value: Boolean) = settingsDataStore.edit { preferences ->
        preferences[SHOW_FOLDERS_ASCENDING] = value
    }

    suspend fun setFolderFilterType(value: PhotoType) = settingsDataStore.edit { preferences ->
        preferences[FOLDERS_FILTER_TYPE] = value.index
    }

    suspend fun setPhotosZoomLevel(value: Int) = settingsDataStore.edit { preferences ->
        preferences[PHOTOS_ZOOM_LEVEL] = value
    }

    companion object {
        private val Context.settingsDataStore by preferencesDataStore("settings_prefs")

        val CACHE_SIZE = intPreferencesKey("cache_size")
        val SHOW_FOLDERS_ASCENDING = booleanPreferencesKey("show_folders_ascending")
        val FOLDERS_FILTER_TYPE = intPreferencesKey("folders_filter_type")
        val PHOTOS_ZOOM_LEVEL = intPreferencesKey("photos_zoom_level")

        const val DEFAULT_CACHE_SIZE = 1024
    }
}
