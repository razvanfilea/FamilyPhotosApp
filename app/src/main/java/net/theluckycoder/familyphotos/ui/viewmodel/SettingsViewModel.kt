package net.theluckycoder.familyphotos.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import net.theluckycoder.familyphotos.BuildConfig
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.repository.LoginRepository
import net.theluckycoder.familyphotos.workers.enqueuePeriodBackupWorker
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val settingsDataStore: SettingsDataStore,
    private val loginRepository: LoginRepository,
    private val userDataStore: UserDataStore,
    private val workManager: WorkManager,
) : ViewModel() {

    val backupOverMobileData: StateFlow<Boolean> = settingsDataStore.backupOverMobileData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cacheSizeMb: StateFlow<Int> = settingsDataStore.cacheSizeMbFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.DEFAULT_CACHE_SIZE)

    private val _cacheUsageBytes = MutableStateFlow(0L)
    val cacheUsageBytes: StateFlow<Long> = _cacheUsageBytes.asStateFlow()

    val appVersion: String = BuildConfig.VERSION_NAME
    val serverAddress: StateFlow<String> = userDataStore.serverAddress
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        refreshCacheUsage()
    }

    fun setBackupOverMobileData(value: Boolean) {
        settingsDataStore.setBackupOverMobileData(value)
        workManager.enqueuePeriodBackupWorker(value)
    }

    fun setCacheSize(value: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.dataStore().edit { it[SettingsDataStore.CACHE_SIZE] = value }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            application.cacheDir.resolve("image_cache").deleteRecursively()
            refreshCacheUsage()
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            loginRepository.logout()
        }
    }

    private fun refreshCacheUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = application.cacheDir.resolve("image_cache")
            val size = if (cacheDir.exists()) {
                cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            } else 0L
            _cacheUsageBytes.value = size
        }
    }
}
