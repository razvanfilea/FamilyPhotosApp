package net.theluckycoder.familyphotos.ui.viewmodel

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.BuildConfig
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.repository.AppUpdateRepository
import net.theluckycoder.familyphotos.core.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.core.data.repository.LoginRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotoUploadRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.core.data.repository.ServerRepository
import net.theluckycoder.familyphotos.domain.GetPhotoUrisUseCase
import net.theluckycoder.familyphotos.domain.RefreshPhotosUseCase
import net.theluckycoder.familyphotos.ui.SnackbarManager
import net.theluckycoder.familyphotos.ui.TopLevelTab
import net.theluckycoder.familyphotos.ui.UiMessageType
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val userDataStore: UserDataStore,
    private val foldersRepository: FoldersRepository,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    private val getPhotoUrisUseCase: GetPhotoUrisUseCase,
    private val serverRepository: ServerRepository,
    val loginRepository: LoginRepository,
    private val snackbarManager: SnackbarManager,
    private val photoUploadRepository: PhotoUploadRepository,
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    private val _localPhotosToDelete = Channel<List<LocalPhoto>>()
    val localPhotosToDelete = _localPhotosToDelete.receiveAsFlow()

    val selectedTabState = mutableStateOf(TopLevelTab.Timeline)

    val isLoggedIn = loginRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isRefreshing = MutableStateFlow(false)
    val isOnline = refreshPhotosUseCase.isOnlineState.asStateFlow()

    val isUpdateAvailable: StateFlow<Boolean> = appUpdateRepository.latestVersion
        .map { latest -> latest != null && latest > BuildConfig.VERSION_CODE }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    sealed interface UpdateDownload {
        data object Idle : UpdateDownload
        /** [progress] is 0..1, or null while the size is unknown. */
        data class Downloading(val progress: Float?) : UpdateDownload
        data object Failed : UpdateDownload
    }

    private val _updateDownload = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
    val updateDownload = _updateDownload.asStateFlow()

    private val _installUpdate = Channel<Intent>(Channel.CONFLATED)
    val installUpdate = _installUpdate.receiveAsFlow()

    fun downloadUpdate() {
        if (_updateDownload.value is UpdateDownload.Downloading) return
        _updateDownload.value = UpdateDownload.Downloading(null)

        viewModelScope.launch {
            val versionCode = appUpdateRepository.latestVersion.value
            val apk = versionCode?.let {
                appUpdateRepository.downloadApk(it) { progress ->
                    _updateDownload.value = UpdateDownload.Downloading(progress)
                }
            }
            if (apk == null) {
                _updateDownload.value = UpdateDownload.Failed
            } else {
                _updateDownload.value = UpdateDownload.Idle
                _installUpdate.send(appUpdateRepository.installIntent(apk))
            }
        }
    }

    init {
        viewModelScope.launch {
            userDataStore.user.collectLatest { newUser ->
                ensureActive()
                if (newUser != null) {
                    refreshPhotos()
                }
            }
        }

        viewModelScope.launch {
            photoUploadRepository.deleteOrphanedSharedFiles()
        }

        viewModelScope.launch {
            // Nothing is announced yet at startup (the latest version isn't persisted), so any
            // APK left over from a previous run is either installed or will be downloaded again
            appUpdateRepository.deleteDownloadedApk()
        }

        viewModelScope.launch {
            foldersRepository.observeMediaStoreChanges().collect {}
        }
    }

    fun refreshPhotos() {
        if (isRefreshing.value) return

        isRefreshing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            when (val result = refreshPhotosUseCase()) {
                is RefreshPhotosUseCase.Result.Error -> {
                    Log.e("MainViewModel", "Failed to refresh data", result.throwable)
                    snackbarManager.showMessage(R.string.error_refresh_failed, UiMessageType.Error)
                }

                RefreshPhotosUseCase.Result.NotLoggedIn -> {
                    loginRepository.logout()
                }

                RefreshPhotosUseCase.Result.Success -> {}
            }

            isRefreshing.value = false
        }
    }

    fun getNetworkPhotosUriAsync(photoIds: LongArray) = viewModelScope.async(Dispatchers.IO) {
        getPhotoUrisUseCase.getNetworkPhotoUris(photoIds)
    }

    suspend fun getNetworkPhotos(photoIds: LongArray): List<NetworkPhoto> {
        return withContext(Dispatchers.IO) {
            photoIds.map { photosRepository.getNetworkPhoto(it) }.filterNotNull()
        }
    }

    fun getLocalPhotosUriAsync(photoIds: LongArray) = viewModelScope.async(Dispatchers.IO) {
        getPhotoUrisUseCase.getLocalPhotoUris(photoIds)
    }

    suspend fun trashNetworkPhotos(photoIds: LongArray): Boolean {
        val result = withContext(Dispatchers.IO) {
            serverRepository.trashNetworkPhoto(photoIds, true)
        }
        if (result) {
            snackbarManager.showPluralMessage(R.plurals.status_photos_trashed, photoIds.size)
        } else {
            snackbarManager.showMessage(R.string.error_trash_failed, UiMessageType.Error)
        }
        return result
    }

    fun deleteLocalPhotos(photoIds: LongArray) {
        viewModelScope.launch(Dispatchers.Default) {
            val photos = photoIds.map { photosRepository.getLocalPhoto(it) }.filterNotNull()
            _localPhotosToDelete.send(photos)
        }
    }

    companion object {
        val PAGING_CONFIG = PagingConfig(
            pageSize = 300,
            initialLoadSize = 300,
            enablePlaceholders = true,
            jumpThreshold = 300
        )
    }
}
