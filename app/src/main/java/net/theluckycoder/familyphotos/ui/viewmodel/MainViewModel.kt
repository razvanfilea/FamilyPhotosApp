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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.repository.LoginRepository
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import net.theluckycoder.familyphotos.domain.GetPhotoUrisUseCase
import net.theluckycoder.familyphotos.domain.RefreshPhotosUseCase
import net.theluckycoder.familyphotos.ui.TopLevelTab
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val userDataStore: UserDataStore,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    private val getPhotoUrisUseCase: GetPhotoUrisUseCase,
    private val serverRepository: ServerRepository,
    val loginRepository: LoginRepository,
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
            userDataStore.userIdFlow.collectLatest { newUserName ->
                ensureActive()
                if (newUserName != null) {
                    refreshPhotos()
                }
            }
        }
    }

    fun refreshPhotos() {
        if (isRefreshing.value) return

        isRefreshing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            when (val result = refreshPhotosUseCase()) {
                is RefreshPhotosUseCase.Result.Error -> Log.e(
                    "MainViewModel",
                    "Failed to refresh data",
                    result.throwable
                )

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

    suspend fun trashNetworkPhotos(photoIds: LongArray) = withContext(Dispatchers.IO) {
        serverRepository.trashNetworkPhoto(photoIds, true)
    }

    fun deleteLocalPhotos(photoIds: LongArray) {
        viewModelScope.launch(Dispatchers.Default) {
            val photos = photoIds.map { photosRepository.getLocalPhoto(it) }.filterNotNull()
            _localPhotosToDelete.send(photos)
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            loginRepository.logout()
        }
    }

    companion object {
        val PAGING_CONFIG = PagingConfig(pageSize = 300, enablePlaceholders = true, jumpThreshold = 300)
    }
}
