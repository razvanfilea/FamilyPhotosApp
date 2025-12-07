package net.theluckycoder.familyphotos.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.repository.LoginRepository
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import net.theluckycoder.familyphotos.domain.RefreshPhotosUseCase
import net.theluckycoder.familyphotos.ui.TopLevelTab
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
    private val userDataStore: UserDataStore,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    val loginRepository: LoginRepository,
    val settingsStore: SettingsDataStore,
) : ViewModel() {

    private val _localPhotosToDelete = Channel<List<LocalPhoto>>()
    val localPhotosToDelete = _localPhotosToDelete.consumeAsFlow()

    val selectedTabState = mutableStateOf(TopLevelTab.Timeline)
    val zoomIndexState = mutableIntStateOf(1)

    val isRefreshing = MutableStateFlow(false)
    val isOnline = refreshPhotosUseCase.isOnlineState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.zoomLevel.first()?.let {
                zoomIndexState.intValue = it
            }

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
        photoIds
            .map { photoId ->
                val photo = photosRepository.getNetworkPhoto(photoId) ?: return@map null
                val localPhoto = photosRepository.getLocalPhotoFromNetwork(photo.id)
                    ?: serverRepository.saveNetworkPhotoToStorage(photo)

                localPhoto?.uri
            }
            .filterNotNull()
    }

    fun getLocalPhotosUriAsync(photoIds: LongArray) = viewModelScope.async(Dispatchers.IO) {
        photoIds.map { photosRepository.getLocalPhoto(it)?.uri }.filterNotNull()
    }

    suspend fun trashNetworkPhotos(photoIds: LongArray) = withContext(Dispatchers.IO) {
        photoIds.map { photoId ->
            async {
                serverRepository.trashNetworkPhoto(photoId, true)
            }
        }
    }.toList().awaitAll()

    fun deleteLocalPhotos(photoIds: LongArray) {
        viewModelScope.launch(Dispatchers.Default) {
            val photos = photoIds.map { photosRepository.getLocalPhoto(it) }.filterNotNull()
            _localPhotosToDelete.send(photos)
        }
    }

    fun getDuplicatesAsync() = viewModelScope.async(Dispatchers.IO) {
        serverRepository.getDuplicates()
    }

    companion object {
        val PAGING_CONFIG = PagingConfig(pageSize = 300, enablePlaceholders = false)
    }
}
