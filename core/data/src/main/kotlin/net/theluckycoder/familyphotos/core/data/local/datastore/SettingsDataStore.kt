package net.theluckycoder.familyphotos.core.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import net.theluckycoder.familyphotos.core.data.di.DefaultCoroutineScope
import net.theluckycoder.familyphotos.core.data.model.FolderSortOrder
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
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

    val localFolderSortOrder: StateFlow<FolderSortOrder> = settingsDataStore.data
        .map { prefs ->
            prefs[LOCAL_FOLDERS_SORT_ORDER]?.let { FolderSortOrder.fromId(it) }
                ?: if (prefs[SHOW_FOLDERS_ASCENDING] == false) FolderSortOrder.NAME_DESC else FolderSortOrder.NAME_ASC
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, FolderSortOrder.NAME_ASC)

    val networkFolderSortOrder: StateFlow<FolderSortOrder> = settingsDataStore.data
        .map { prefs ->
            prefs[NETWORK_FOLDERS_SORT_ORDER]?.let { FolderSortOrder.fromId(it) }
                ?: if (prefs[SHOW_FOLDERS_ASCENDING] == false) FolderSortOrder.NAME_DESC else FolderSortOrder.NAME_ASC
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, FolderSortOrder.NAME_ASC)

    val showFoldersAsGrid: StateFlow<Boolean> = settingsDataStore.data
        .map { it[SHOW_FOLDERS_AS_GRID] != false }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, true)

    val photoType: StateFlow<PhotoType> = settingsDataStore.data
        .map { it.toPhotoType() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, PhotoType.All)

    private fun Preferences.toPhotoType() = when (this[FOLDERS_FILTER_TYPE]) {
        PhotoType.Personal.index -> PhotoType.Personal
        PhotoType.Family.index -> PhotoType.Family
        PhotoType.Shared.index -> PhotoType.Shared
        else -> PhotoType.All
    }

    val backupOverMobileData: Flow<Boolean> =
        settingsDataStore.data.map { it[BACKUP_OVER_MOBILE_DATA] == true }.distinctUntilChanged()

    fun setBackupOverMobileData(value: Boolean) = scope.launch {
        settingsDataStore.edit { it[BACKUP_OVER_MOBILE_DATA] = value }
    }

    val notifySharedFolders: Flow<Boolean> =
        settingsDataStore.data.map { it[NOTIFY_SHARED_FOLDERS] != false }.distinctUntilChanged()

    suspend fun setNotifySharedFolders(value: Boolean) {
        settingsDataStore.edit { it[NOTIFY_SHARED_FOLDERS] = value }
    }

    val notifyMemories: Flow<Boolean> =
        settingsDataStore.data.map { it[NOTIFY_MEMORIES] != false }.distinctUntilChanged()

    suspend fun setNotifyMemories(value: Boolean) {
        settingsDataStore.edit { it[NOTIFY_MEMORIES] = value }
    }

    /** Photos already announced in a memories notification, so the overlapping daily windows don't repeat them. */
    suspend fun getNotifiedMemoryIds(): Set<Long> =
        settingsDataStore.data.first()[NOTIFIED_MEMORY_IDS].orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }.toSet()

    suspend fun setNotifiedMemoryIds(ids: Set<Long>) {
        settingsDataStore.edit { it[NOTIFIED_MEMORY_IDS] = ids.joinToString(",") }
    }

    suspend fun getLastMemoriesDay(): Long = settingsDataStore.data.first()[LAST_MEMORIES_DAY] ?: -1L

    suspend fun setLastMemoriesDay(epochDay: Long) {
        settingsDataStore.edit { it[LAST_MEMORIES_DAY] = epochDay }
    }

    suspend fun clearMemoriesState() {
        settingsDataStore.edit {
            it.remove(LAST_MEMORIES_DAY)
            it.remove(NOTIFIED_MEMORY_IDS)
        }
    }

    fun setLocalFolderSortOrder(order: FolderSortOrder) = scope.launch {
        settingsDataStore.edit { it[LOCAL_FOLDERS_SORT_ORDER] = order.id }
    }

    fun setNetworkFolderSortOrder(order: FolderSortOrder) = scope.launch {
        settingsDataStore.edit { it[NETWORK_FOLDERS_SORT_ORDER] = order.id }
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
        val LOCAL_FOLDERS_SORT_ORDER = intPreferencesKey("local_folders_sort_order")
        val NETWORK_FOLDERS_SORT_ORDER = intPreferencesKey("network_folders_sort_order")
        val SHOW_FOLDERS_AS_GRID = booleanPreferencesKey("show_folders_as_grid")
        val FOLDERS_FILTER_TYPE = intPreferencesKey("folders_filter_type")
        val PHOTOS_ZOOM_LEVEL = intPreferencesKey("photos_zoom_level")
        val BACKUP_OVER_MOBILE_DATA = booleanPreferencesKey("backup_over_mobile_data")
        val LAST_MEMORIES_DAY = longPreferencesKey("last_memories_day")
        val NOTIFIED_MEMORY_IDS = stringPreferencesKey("notified_memory_ids")
        val NOTIFY_MEMORIES = booleanPreferencesKey("notify_memories")
        val NOTIFY_SHARED_FOLDERS = booleanPreferencesKey("notify_shared_folders")

        const val DEFAULT_CACHE_SIZE = 1024
        const val DEFAULT_ZOOM_LEVEL = 1
    }
}
