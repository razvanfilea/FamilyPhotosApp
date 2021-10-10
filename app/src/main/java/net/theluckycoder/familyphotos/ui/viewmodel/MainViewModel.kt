package net.theluckycoder.familyphotos.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import androidx.work.*
import com.jakewharton.processphoenix.ProcessPhoenix
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.theluckycoder.familyphotos.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.datastore.UserDataStore
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.repository.FoldersRepository
import net.theluckycoder.familyphotos.repository.PhotosListRepository
import net.theluckycoder.familyphotos.repository.PhotosRepository
import net.theluckycoder.familyphotos.workers.UploadWorker
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    private val photosRepository: PhotosRepository,
    private val photosListRepository: PhotosListRepository,
    private val foldersRepository: FoldersRepository,
    userDataStore: UserDataStore,
    val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val workManager: WorkManager = WorkManager.getInstance(app)

    private var userId = -1L

    // Data
    private val isOnlineFlow = MutableStateFlow(false)
    val isOnline = isOnlineFlow.asStateFlow()

    val displayNameFlow = userDataStore.displayNameFlow

    val allPhotosPaging = Pager(PagingConfig(pageSize = 100, enablePlaceholders = false)) {
        photosListRepository.getPersonalPhotosPaged(userId)
    }.flow.flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)
        .mapPagingPhotos()

    val publicPhotosPaging = Pager(PagingConfig(pageSize = 100, enablePlaceholders = false)) {
        photosListRepository.getPublicPhotosPaged(userId)
    }.flow.flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)
        .mapPagingPhotos()

    val localFolders = foldersRepository.localFoldersFlow

    val networkFolders = foldersRepository.networkFoldersFlow.map {
        it.map { folder -> folder.copy(isPublic = folder.ownerUserId != userId) }
    }

    // Ui
    val isRefreshing = MutableStateFlow(false)
    val showBottomAppBar = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            userDataStore.userIdFlow.collectLatest {
                ensureActive()
                val newId = it.toLong()
                if (userId != newId) {
                    userId = newId
                    refreshPhotos()
                }
            }
        }
    }

    fun refreshPhotos() {
        isRefreshing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val ping = async {
                try {
                    isOnlineFlow.value = photosListRepository.pingServer()
                } catch (e: Exception) {
                    false
                }
            }

            val localPhotos = async { foldersRepository.updatePhoneAlbums() }

            try {
                photosListRepository.downloadAllPhotos(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            ping.await()
            localPhotos.await()

            isRefreshing.value = false
        }
    }

    fun getNetworkFolderPhotos(folder: String) =
        foldersRepository.networkPhotosFromFolder(folder)

    fun getLocalFolderPhotos(folder: String) =
        foldersRepository.localPhotosFromFolder(folder)

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
                val photo = photosRepository.getNetworkPhoto(id) ?: return@map false

                photosRepository.changePhotoLocation(
                    photo = photo,
                    newUserOwnerId = userId.takeUnless { makePublic },
                    newFolderName = newFolderName
                )
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }.all { it }
    }

    // region Individual Photos Actions

    suspend fun getLocalPhoto(photoId: Long): LocalPhoto? = withContext(Dispatchers.IO) {
        photosRepository.getLocalPhoto(photoId)
    }

    suspend fun getNetworkPhoto(photoId: Long): NetworkPhoto? = withContext(Dispatchers.IO) {
        photosRepository.getNetworkPhoto(photoId)
    }

    /**
     * Receives a list of localPhoto ids that will be uploaded
     */
    fun uploadPhotosAsync(
        localPhotos: List<Long>,
        public: Boolean,
        uploadFolder: String?
    ) {
        val data = Data.Builder()
            .putAll(
                mapOf(
                    UploadWorker.KEY_INPUT_LIST to localPhotos.toLongArray(),
                    UploadWorker.KEY_USER_OWNER_ID to userId.takeUnless { public },
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
            .setBackoffCriteria(BackoffPolicy.LINEAR, 3, TimeUnit.SECONDS)
            .build()

        workManager
//            .beginUniqueWork("upload_work", ExistingWorkPolicy.APPEND, uploadRequest)
            .enqueue(uploadRequest)
    }

    /**
     * TODO: Document this
     */
    fun getLocalPhotoUri(photo: Photo): Deferred<Uri?> = viewModelScope.async(Dispatchers.IO) {
        when (photo) {
            is LocalPhoto -> photo.uri
            is NetworkPhoto -> {
                val localPhoto = photosRepository.getLocalPhotoFromNetwork(photo.id)
                    ?: photosRepository.saveNetworkPhotoToStorage(photo)

                localPhoto?.uri
            }
        }
    }

    fun deletePhotoAsync(photo: Photo): Deferred<Boolean> =
        viewModelScope.async(Dispatchers.IO) {
            when (photo) {
                is NetworkPhoto ->
                    photosRepository.deleteNetworkPhoto(photo.ownerUserId, photo.id)
                is LocalPhoto ->
                    photosRepository.deleteLocalPhoto(photo)
            }
        }

    // endregion

    @OptIn(ExperimentalTime::class)
    fun clearAppCache(app: Application) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                withTimeout(Duration.seconds(10)) {
                    while (!app.cacheDir.deleteRecursively())
                        ensureActive()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "App cache cleaned successfully", Toast.LENGTH_SHORT)
                            .show()
                        Log.d("Cache", "App cache cleaned successfully")
                    }
                }
            }

            ProcessPhoenix.triggerRebirth(app)
        }
    }

    companion object {
        private val currentCalender = Calendar.getInstance()

        private fun Flow<PagingData<NetworkPhoto>>.mapPagingPhotos() = map { pagingData ->
            pagingData
                .insertSeparators { before, after ->
                    after ?: return@insertSeparators null

                    val beforeCalender = if (before != null) {
                        Calendar.getInstance().apply { timeInMillis = before.timeCreated }
                    } else null

                    val afterCalendar = Calendar.getInstance()
                    afterCalendar.timeInMillis = after.timeCreated

                    if (beforeCalender == null
                        || beforeCalender.get(Calendar.MONTH) != afterCalendar.get(Calendar.MONTH)
                        || beforeCalender.get(Calendar.YEAR) != afterCalendar.get(Calendar.YEAR)
                    ) {
                        buildString {
                            append(DateFormat.format("MMMM", afterCalendar))

                            val year = afterCalendar.get(Calendar.YEAR)
                            if (year != currentCalender.get(Calendar.YEAR))
                                append(' ').append(year)
                        }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    } else null
                }
        }
    }
}
