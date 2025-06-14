package net.theluckycoder.familyphotos.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import com.jakewharton.processphoenix.ProcessPhoenix
import dagger.Lazy
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.theluckycoder.familyphotos.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.datastore.UserDataStore
import net.theluckycoder.familyphotos.db.dao.LocalFolderBackupDao
import net.theluckycoder.familyphotos.model.LocalFolderToBackup
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkFolder
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.PUBLIC_USER_ID
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.model.PhotoType
import net.theluckycoder.familyphotos.model.isPublic
import net.theluckycoder.familyphotos.network.service.UserService
import net.theluckycoder.familyphotos.repository.FoldersRepository
import net.theluckycoder.familyphotos.repository.PhotosRepository
import net.theluckycoder.familyphotos.repository.ServerRepository
import net.theluckycoder.familyphotos.use_case.RefreshPhotosUseCase
import net.theluckycoder.familyphotos.workers.UploadWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
    private val foldersRepository: FoldersRepository,
    private val foldersToBackupDao: LocalFolderBackupDao,
    private val userDataStore: UserDataStore,
    private val userService: Lazy<UserService>,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    val settingsStore: SettingsDataStore,
) : ViewModel() {

    private val workManager: WorkManager = WorkManager.getInstance(app)

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    val favoritePhotosFlow = Pager(PAGING_CONFIG) {
        photosRepository.getFavoritePhotosPaged()
    }.flow.cachedIn(viewModelScope)

    val localFolders = settingsStore.showFoldersAscending
        .flatMapLatest { foldersRepository.localFoldersFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val networkFolders = settingsStore.showFoldersAscending
        .flatMapLatest { foldersRepository.networkFoldersFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

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
                is RefreshPhotosUseCase.Result.Error -> Log.e("MainViewModel", "Failed to refresh data")
                RefreshPhotosUseCase.Result.NotLoggedIn -> {
                    logout()
                }
                RefreshPhotosUseCase.Result.Success -> {}
            }

            isRefreshing.value = false
        }
    }

    fun refreshLocalPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            foldersRepository.updatePhoneAlbums()
        }
    }

    private val _selectedNetworkFolder = MutableStateFlow<String?>(null)
    val currentNetworkFolderPhotosPager: Flow<PagingData<NetworkPhoto>> = _selectedNetworkFolder
        .flatMapLatest { folderName ->
            if (folderName != null) {
                Pager(PAGING_CONFIG) {
                    foldersRepository.networkPhotosFromFolderPaged(folderName)
                }.flow.cachedIn(viewModelScope)
            } else {
                emptyFlow()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, PagingData.empty())

    fun loadNetworkFolderPhotos(folderName: String?) {
        _selectedNetworkFolder.value = folderName
    }

    private val _selectedLocalFolder = MutableStateFlow<String?>(null)
    val currentLocalFolderPhotosPager: Flow<PagingData<LocalPhoto>> = _selectedLocalFolder
        .flatMapLatest { folderName ->
            if (folderName != null) {
                Pager(PAGING_CONFIG) {
                    foldersRepository.localPhotosFromFolderPaged(folderName)
                }.flow.cachedIn(viewModelScope)
            } else {
                emptyFlow()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, PagingData.empty())

    fun loadLocalFolderPhotos(folderName: String?) {
        _selectedLocalFolder.value = folderName
    }

    fun isLocalFolderBackupUp(folder: String): Flow<Boolean> =
        foldersToBackupDao.getAll().map { it.firstOrNull { it == folder } != null }

    fun backupLocalFolder(folder: String, add: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (add) {
                foldersToBackupDao.insert(LocalFolderToBackup(folder))
            } else {
                foldersToBackupDao.delete(LocalFolderToBackup(folder))
            }
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
                val photo = photosRepository.getNetworkPhoto(id).firstOrNull() ?: return@map false

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

    // region Individual Photos Actions

    fun getLocalPhotoFlow(photoId: Long): Flow<LocalPhoto?> =
        photosRepository.getLocalPhoto(photoId)

    fun getNetworkPhotoFlow(photoId: Long): Flow<NetworkPhoto?> =
        photosRepository.getNetworkPhoto(photoId)

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

    fun renameNetworkFolder(
        folder: NetworkFolder,
        makePublic: Boolean,
        newName: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            serverRepository.renameNetworkFolder(
                folder,
                makePublic,
                newName.trim().takeIf { it.isNotEmpty() }
            )
        }
    }

    /**
     * This functions returns an Uri for a certain image,
     * if it is a [LocalPhoto] it simply returns its location,
     * if it is a [NetworkPhoto] it downloads and stores the image locally and then returns its Uri
     * or returns null if the operations fails
     */
    fun getPhotoLocalUriAsync(photo: Photo): Deferred<Uri?> = viewModelScope.async(Dispatchers.IO) {
        when (photo) {
            is LocalPhoto -> photo.uri
            is NetworkPhoto -> {
                val localPhoto = photosRepository.getLocalPhotoFromNetwork(photo.id)
                    ?: serverRepository.saveNetworkPhotoToStorage(photo)

                localPhoto?.uri
            }
        }
    }

    // endregion

    suspend fun deleteNetworkPhotos(photos: List<NetworkPhoto>) {
        withContext(Dispatchers.IO) {
            photos.map { photo ->
                async {
                    if (serverRepository.deleteNetworkPhoto(photo.id)) {
                        photosRepository.removeNetworkReference(photo)
                    }
                }
            }.toList().map { it.await() }
        }
    }

    fun deleteLocalPhotos(photos: List<LocalPhoto>) {
        viewModelScope.launch(Dispatchers.Default) {
            _localPhotosToDelete.send(photos)
        }
    }


    fun clearAppCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                withTimeout(10.seconds) {
                    while (!app.cacheDir.deleteRecursively())
                        ensureActive()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "Cache cleaned successfully", Toast.LENGTH_SHORT)
                            .show()
                        Log.d("Cache", "Cache cleaned successfully")
                    }
                }
            }

            ProcessPhoenix.triggerRebirth(app)
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                userService.get().logout()
            } catch (_: Exception) {
            }
            userDataStore.clear()

            ProcessPhoenix.triggerRebirth(app)
        }
    }

    companion object {
        private val PAGING_CONFIG = PagingConfig(pageSize = 70, enablePlaceholders = false)
    }
}
