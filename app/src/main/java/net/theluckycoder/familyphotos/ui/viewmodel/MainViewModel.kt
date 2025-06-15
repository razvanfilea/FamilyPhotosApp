package net.theluckycoder.familyphotos.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.data.model.LocalPhoto
import net.theluckycoder.familyphotos.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.PUBLIC_USER_ID
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.model.isPublic
import net.theluckycoder.familyphotos.data.repository.LoginRepository
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import net.theluckycoder.familyphotos.domain.RefreshPhotosUseCase
import net.theluckycoder.familyphotos.workers.UploadWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
    private val userDataStore: UserDataStore,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    val loginRepository: LoginRepository,
    private val workManager: WorkManager,
    val settingsStore: SettingsDataStore,
) : ViewModel() {

    val selectedPhotoType =
        settingsStore.photoType.stateIn(viewModelScope, SharingStarted.Eagerly, PhotoType.All)

    val timelinePager = Pager(PAGING_CONFIG) {
        photosRepository.getAllPhotosPaged()
    }.flow
        .cachedIn(viewModelScope)
        .combine(selectedPhotoType) { photos, photoType ->
            if (photoType == PhotoType.All) {
                return@combine photos
            }
            photos.filter {
                when (photoType) {
                    PhotoType.All -> true
                    PhotoType.Personal -> !it.isPublic()
                    PhotoType.Family -> it.isPublic()
                }
            }
        }

    val memories = selectedPhotoType
        .combine(userDataStore.userIdFlow) { photoType, userName ->
            when (photoType) {
                PhotoType.All -> null
                PhotoType.Personal -> userName
                PhotoType.Family -> PUBLIC_USER_ID
            }
        }
        .flatMapLatest { userName -> photosRepository.getMemories(userName) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    private val _localPhotosToDelete = Channel<List<LocalPhoto>>()
    val localPhotosToDelete = _localPhotosToDelete.consumeAsFlow()

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
            val result = refreshPhotosUseCase()

            when (result) {
                is RefreshPhotosUseCase.Result.Error -> Log.e(
                    "MainViewModel",
                    "Failed to refresh data"
                )

                RefreshPhotosUseCase.Result.NotLoggedIn -> {
                    loginRepository.logout()
                }

                RefreshPhotosUseCase.Result.Success -> {}
            }

            isRefreshing.value = false
        }
    }

    /**
     * Change the folder and the user where these [NetworkPhoto]s belong
     *
     * @returns true if all photos have been successfully moved
     */
    fun changePhotosLocationAsync(
        networkPhotos: List<Long>,
        makePublic: Boolean,
        newFolderName: String?
    ): Deferred<Boolean> = viewModelScope.async(Dispatchers.IO) {
        networkPhotos.map { id ->
            try {
                val photo =
                    photosRepository.getNetworkPhotoFlow(id).firstOrNull() ?: return@map false

                val result = serverRepository.changePhotoLocation(
                    photo = photo,
                    makePublic = makePublic,
                    newFolderName = newFolderName
                )
                Log.d("Moving Photos", "Moved $id result=$result")
                result
            } catch (e: Exception) {
                Log.e("Moving Photos", "Moved $id failed!", e)
                false
            }
        }.all { it }
    }

    fun getLocalPhotoFlow(photoId: Long): Flow<LocalPhoto?> =
        photosRepository.getLocalPhotoFlow(photoId)

    fun getNetworkPhotoFlow(photoId: Long): Flow<NetworkPhoto?> =
        photosRepository.getNetworkPhotoFlow(photoId)

    /**
     * Receives a list of [LocalPhoto] ids that will be uploaded
     */
    fun uploadPhotosAsync(
        localPhotos: List<Long>,
        makePublic: Boolean,
        uploadFolder: String?
    ): Operation {
        val data = Data.Builder()
            .putAll(
                mapOf(
                    UploadWorker.KEY_INPUT_LIST to localPhotos.toLongArray(),
                    UploadWorker.KEY_MAKE_PUBLIC to makePublic,
                    UploadWorker.KEY_UPLOAD_FOLDER to uploadFolder
                )
            )
            .build()

        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_ROAMING)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .addTag(UploadWorker.TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
            .build()

        return workManager
//            .beginUniqueWork("upload_work", ExistingWorkPolicy.APPEND, uploadRequest)
            .enqueue(uploadRequest)
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

    suspend fun deleteNetworkPhotos(photoIds: LongArray) = withContext(Dispatchers.IO) {
        photoIds.map { photoId ->
            async {
                val networkPhoto = photosRepository.getNetworkPhoto(photoId)
                if (networkPhoto != null && serverRepository.deleteNetworkPhoto(photoId)) {
                    photosRepository.removeNetworkReference(networkPhoto)
                }
            }
        }
    }.toList().map { it.await() }

    fun deleteLocalPhotos(photoIds: LongArray) {
        viewModelScope.launch(Dispatchers.Default) {
            val photos = photoIds.map { photosRepository.getLocalPhoto(it) }.filterNotNull()
            _localPhotosToDelete.send(photos)
        }
    }

    companion object {
        val PAGING_CONFIG = PagingConfig(pageSize = 70, enablePlaceholders = false)
    }
}
