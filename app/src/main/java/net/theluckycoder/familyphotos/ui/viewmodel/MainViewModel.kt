package net.theluckycoder.familyphotos.ui.viewmodel

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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.core.data.repository.LoginRepository
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
) : ViewModel() {

    private val _localPhotosToDelete = Channel<List<LocalPhoto>>()
    val localPhotosToDelete = _localPhotosToDelete.consumeAsFlow()

    val selectedTabState = mutableStateOf(TopLevelTab.Timeline)

    val isLoggedIn = loginRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isRefreshing = MutableStateFlow(false)
    val isOnline = refreshPhotosUseCase.isOnlineState.asStateFlow()

    init {
        viewModelScope.launch {
            userDataStore.user.collectLatest { newUser->
                ensureActive()
                if (newUser!= null) {
                    refreshPhotos()
                }
            }
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
        val PAGING_CONFIG = PagingConfig(pageSize = 300, enablePlaceholders = true, jumpThreshold = 300)
    }
}
