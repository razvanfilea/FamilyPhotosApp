package net.theluckycoder.familyphotos.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.jakewharton.processphoenix.ProcessPhoenix
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import net.theluckycoder.familyphotos.PhotosApp.Companion.LOCAL_TIME_ZONE
import net.theluckycoder.familyphotos.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.datastore.UserDataStore
import net.theluckycoder.familyphotos.model.ExifData
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkFolder
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.network.service.UserService
import net.theluckycoder.familyphotos.repository.FoldersRepository
import net.theluckycoder.familyphotos.repository.PhotosRepository
import net.theluckycoder.familyphotos.repository.ServerRepository
import net.theluckycoder.familyphotos.workers.BackupWorker
import net.theluckycoder.familyphotos.workers.UploadWorker
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
    private val foldersRepository: FoldersRepository,
    private val userDataStore: UserDataStore,
    private val userService: Lazy<UserService>,
    val settingsStore: SettingsDataStore,
) : ViewModel() {

    private val workManager: WorkManager = WorkManager.getInstance(app)

    private var userName = ""

    // Data
    private val _isOnlineFlow = MutableStateFlow(false)
    val isOnlineFlow = _isOnlineFlow.asStateFlow()

    val displayNameFlow = userDataStore.displayNameFlow
    val autoBackupFlow = userDataStore.autoBackup

    val personalPhotosPager = Pager(PAGING_CONFIG) {
        photosRepository.getPersonalPhotosPaged(userName)
    }.flow.flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)
//        .mapPagingPhotos()

    val publicPhotosPager = Pager(PAGING_CONFIG) {
        photosRepository.getPublicPhotosPaged()
    }.flow.flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)
//        .mapPagingPhotos()

    val favoritePhotosFlow = Pager(PAGING_CONFIG) {
        photosRepository.getFavoritePhotosPaged()
    }.flow.cachedIn(viewModelScope)


    val localFolders = foldersRepository.localFoldersFlow

    val networkFolders = foldersRepository.networkFoldersFlow

    private val _localPhotosToDelete = Channel<List<LocalPhoto>>()
    val localPhotosToDelete = _localPhotosToDelete.consumeAsFlow()

    // Ui
    val isRefreshing = MutableStateFlow(false)
    val zoomIndexState = mutableIntStateOf(1)
    val showBars = mutableStateOf(true)

    init {
        viewModelScope.launch {

            userDataStore.userIdFlow.collectLatest { newUserName ->
                ensureActive()
                if (newUserName != null && userName != newUserName) {
                    userName = newUserName
                    refreshPhotos(app)
                }
            }
        }

        viewModelScope.launch {
            autoBackupFlow.collectLatest { autoUpload ->
                ensureActive()

                if (autoUpload) {
                    val constraints = Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()

                    val periodicUpload =
                        PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                            .setConstraints(constraints)
                            .build()

                    try {
                        WorkManager.getInstance(app)
                            .enqueueUniquePeriodicWork(
                                UNIQUE_PERIODIC_UPLOAD,
                                ExistingPeriodicWorkPolicy.KEEP,
                                periodicUpload
                            )
                            .await()
                        Log.i(BackupWorker::class.simpleName, "Backup has been enabled")
                    } catch (e: Throwable) {
                        Log.e(BackupWorker::class.simpleName, "Backup failed to be enabled", e)
                    }
                } else {
                    try {
                        WorkManager.getInstance(app)
                            .cancelUniqueWork(UNIQUE_PERIODIC_UPLOAD)
                            .await()
                        Log.i(BackupWorker::class.simpleName, "Backup has been disabled")
                    } catch (e: Throwable) {
                        Log.e(BackupWorker::class.simpleName, "Backup failed to be disabled", e)
                    }
                }
            }
        }
    }

    fun refreshPhotos(app: Application) {
        if (isRefreshing.value) return

        isRefreshing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val pingResponse = try {
                serverRepository.pingServer()
            } catch (e: Exception) {
                ServerRepository.PingResponse.UNSUCCESSFUL
            }

            if (pingResponse == ServerRepository.PingResponse.NOT_LOGGED_IN) {
                logout(app)
                return@launch
            }
            _isOnlineFlow.value = pingResponse == ServerRepository.PingResponse.SUCCESSFUL

            val localPhotos = async { foldersRepository.updatePhoneAlbums() }

            try {
                serverRepository.downloadAllPhotos()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            localPhotos.await()
            photosRepository.removeMissingNetworkReferences()

            isRefreshing.value = false
        }
    }

    fun refreshLocalPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            foldersRepository.updatePhoneAlbums()
        }
    }

    fun setAutoBackup(value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userDataStore.setAutoBackup(value)
        }
    }

    private suspend fun getMemories(callback: suspend (timestamp: Long) -> List<NetworkPhoto>) =
        withContext(Dispatchers.Default) {
            val instant = Clock.System.now()
            val today = instant.toLocalDateTime(LOCAL_TIME_ZONE).date

            List(7) {
                today.minus(it + 1, DateTimeUnit.YEAR)
            }.map {
                val yearsUntil = it.yearsUntil(today)
                val timestamp =
                    it.atTime(12, 0).toInstant(LOCAL_TIME_ZONE).toEpochMilliseconds() / 1000

                yearsUntil to callback(timestamp)
            }.filterNot {
                it.second.isEmpty()
            }
        }

    suspend fun getPersonalMemories() = getMemories {
        photosRepository.getMemories(it, userName).first()
    }

    suspend fun getPublicMemories() = getMemories {
        photosRepository.getMemories(it).first()
    }

    fun getNetworkFolderPhotosPaged(folder: String) = Pager(PAGING_CONFIG) {
        foldersRepository.networkPhotosFromFolderPaged(folder)
    }.flow

    fun getLocalFolderPhotosPaged(folder: String) = Pager(PAGING_CONFIG) {
        foldersRepository.localPhotosFromFolderPaged(folder)
    }.flow

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

    suspend fun getExifData(photo: NetworkPhoto): ExifData? = withContext(Dispatchers.IO) {
        serverRepository.getExifData(photo)
    }

    /**
     * Receives a list of [LocalPhoto] ids that will be uploaded
     */
    fun uploadPhotosAsync(
        localPhotos: List<Long>,
        makePublic: Boolean,
        uploadFolder: String?
    ) {
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
            .setBackoffCriteria(BackoffPolicy.LINEAR, 3, TimeUnit.SECONDS)
            .build()

        workManager
//            .beginUniqueWork("upload_work", ExistingWorkPolicy.APPEND, uploadRequest)
            .enqueue(uploadRequest)
    }

    fun renameNetworkFolder(
        folder: NetworkFolder,
        makePublic: Boolean,
        newName: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            serverRepository.renameNetworkFolder(folder, makePublic, newName)
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


    fun clearAppCache(app: Application) {
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

    fun logout(app: Application) {
        viewModelScope.launch {
            try {
                userService.get().logout()
            } catch (_: Exception) {
            }
            userDataStore.clear()
        }

        ProcessPhoenix.triggerRebirth(app)
    }

    companion object {
        private const val UNIQUE_PERIODIC_UPLOAD = "periodic_upload"

        private val PAGING_CONFIG = PagingConfig(pageSize = 70, enablePlaceholders = false)

        private val currentDate = Clock.System.now().toLocalDateTime(LOCAL_TIME_ZONE)

        fun computeSeparatorText(before: Photo?, after: Photo): String? {
            val beforeDate = before?.let {
                val instant = Instant.fromEpochSeconds(it.timeCreated)
                instant.toLocalDateTime(LOCAL_TIME_ZONE)
            }
            val afterDate =
                Instant.fromEpochSeconds(after.timeCreated)
                    .toLocalDateTime(LOCAL_TIME_ZONE)

            return if (beforeDate == null || beforeDate.monthNumber != afterDate.monthNumber || beforeDate.year != afterDate.year
            ) {
                buildDateString(afterDate)
            } else null
        }

        private fun buildDateString(afterDate: LocalDateTime) = buildString {
            append(
                afterDate.month.getDisplayName(
                    TextStyle.FULL,
                    Locale.forLanguageTag("ro-RO")
                )
            )

            val year = afterDate.year
            if (year != currentDate.year)
                append(' ').append(year)
        }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
