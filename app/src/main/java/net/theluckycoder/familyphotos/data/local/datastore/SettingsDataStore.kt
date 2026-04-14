package net.theluckycoder.familyphotos.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.di.DefaultCoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext context: Context,
    private val scope: DefaultCoroutineScope,
) {

    private val settingsDataStore = context.settingsDataStore

    private val _zoomLevel = MutableStateFlow(DEFAULT_ZOOM_LEVEL)
    val zoomLevel: StateFlow<Int> = _zoomLevel.asStateFlow()

    init {
        scope.launch {
            settingsDataStore.data.first()[PHOTOS_ZOOM_LEVEL]?.let { _zoomLevel.value = it }
        }
    }

    fun dataStore() = settingsDataStore

    val cacheSizeMbFlow: Flow<Int> =
        settingsDataStore.data.map { it[CACHE_SIZE] ?: DEFAULT_CACHE_SIZE }.distinctUntilChanged()

    val showFoldersAscending: StateFlow<Boolean> = settingsDataStore.data
        .map { it[SHOW_FOLDERS_ASCENDING] != false }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, true)

    val showFoldersAsGrid: StateFlow<Boolean> = settingsDataStore.data
        .map { it[SHOW_FOLDERS_AS_GRID] != false }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, true)

    val photoType: StateFlow<PhotoType> = settingsDataStore.data
        .map {
            when (it[FOLDERS_FILTER_TYPE]) {
                PhotoType.Personal.index -> PhotoType.Personal
                PhotoType.Family.index -> PhotoType.Family
                else -> PhotoType.All
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, PhotoType.All)

    val backupOverMobileData: Flow<Boolean> =
        settingsDataStore.data.map { it[BACKUP_OVER_MOBILE_DATA] == true }.distinctUntilChanged()

    fun setBackupOverMobileData(value: Boolean) = scope.launch {
        settingsDataStore.edit { it[BACKUP_OVER_MOBILE_DATA] = value }
    }

    fun setShowFoldersAscending(value: Boolean) = scope.launch {
        settingsDataStore.edit { it[SHOW_FOLDERS_ASCENDING] = value }
    }

    fun setShowFoldersAsGrid(value: Boolean) = scope.launch {
        settingsDataStore.edit { it[SHOW_FOLDERS_AS_GRID] = value }
    }

    fun setSelectedPhotoType(value: PhotoType) = scope.launch {
        settingsDataStore.edit { it[FOLDERS_FILTER_TYPE] = value.index }
    }

    fun setPhotosZoomLevel(value: Int) {
        _zoomLevel.value = value
        scope.launch { settingsDataStore.edit { it[PHOTOS_ZOOM_LEVEL] = value } }
    }

    companion object {
        private val Context.settingsDataStore by preferencesDataStore("settings_prefs")

        val CACHE_SIZE = intPreferencesKey("cache_size")
        val SHOW_FOLDERS_ASCENDING = booleanPreferencesKey("show_folders_ascending")
        val SHOW_FOLDERS_AS_GRID = booleanPreferencesKey("show_folders_as_grid")
        val FOLDERS_FILTER_TYPE = intPreferencesKey("folders_filter_type")
        val PHOTOS_ZOOM_LEVEL = intPreferencesKey("photos_zoom_level")
        val BACKUP_OVER_MOBILE_DATA = booleanPreferencesKey("backup_over_mobile_data")

        const val DEFAULT_CACHE_SIZE = 1024
        const val DEFAULT_ZOOM_LEVEL = 1
    }
}
