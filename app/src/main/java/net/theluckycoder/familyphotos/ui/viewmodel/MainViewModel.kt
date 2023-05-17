package net.theluckycoder.familyphotos.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
import net.theluckycoder.familyphotos.PhotosApp.Companion.TIME_ZONE
import net.theluckycoder.familyphotos.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.datastore.UserDataStore
import net.theluckycoder.familyphotos.model.ExifData
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.repository.FoldersRepository
import net.theluckycoder.familyphotos.repository.PhotosRepository
import net.theluckycoder.familyphotos.repository.ServerRepository
import net.theluckycoder.familyphotos.workers.BackupWorker
import net.theluckycoder.familyphotos.workers.UploadWorker
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.streams.toList
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
    private val foldersRepository: FoldersRepository,
    private val userDataStore: UserDataStore,
    val settingsStore: SettingsDataStore,
) : ViewModel() {

    private val workManager: WorkManager = WorkManager.getInstance(app)

    private var userId = -1L

    // Data
    private val _isOnlineFlow = MutableStateFlow(false)
    val isOnlineFlow = _isOnlineFlow.asStateFlow()

    val displayNameFlow = userDataStore.displayNameFlow
    val autoBackupFlow = userDataStore.autoBackup

    val personalPhotosPager = Pager(PagingConfig(pageSize = 120, enablePlaceholders = false)) {
        photosRepository.getPersonalPhotosPaged(userId)
    }.flow.flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)
        .mapPagingPhotos()

    val publicPhotosPager = Pager(PagingConfig(pageSize = 120, enablePlaceholders = false)) {
        photosRepository.getPublicPhotosPaged()
    }.flow.flowOn(Dispatchers.Default)
        .cachedIn(viewModelScope)
        .mapPagingPhotos()

    val localFolders = foldersRepository.localFoldersFlow

    val networkFolders = foldersRepository.networkFoldersFlow

    // Ui
    val isRefreshing = MutableStateFlow(false)

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

    fun refreshPhotos() {
        isRefreshing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val ping = async {
                try {
                    _isOnlineFlow.value = serverRepository.pingServer()
                } catch (e: Exception) {
                    false
                }
            }

            val localPhotos = async { foldersRepository.updatePhoneAlbums() }

            try {
                serverRepository.downloadAllPhotos(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            ping.await()
            localPhotos.await()

            isRefreshing.value = false
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
            val today = instant.toLocalDateTime(TIME_ZONE).date

            listOf(
                today.minus(DateTimeUnit.YEAR),
                today.minus(DateTimeUnit.YEAR * 2),
                today.minus(DateTimeUnit.YEAR * 3),
                today.minus(DateTimeUnit.YEAR * 4),
                today.minus(DateTimeUnit.YEAR * 5),
                today.minus(DateTimeUnit.YEAR * 6),
            ).map {
                val yearsUntil = it.yearsUntil(today)
                val timestamp = it.atTime(12, 0).toInstant(TIME_ZONE).toEpochMilliseconds() / 1000

                yearsUntil to callback(timestamp)
            }.filterNot {
                it.second.isEmpty()
            }
        }

    suspend fun getPersonalMemories() = getMemories {
        photosRepository.getMemories(it, userId).first()
    }

    suspend fun getPublicMemories() = getMemories {
        photosRepository.getMemories(it).first()
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
                val photo = photosRepository.getNetworkPhoto(id).firstOrNull() ?: return@map false

                val result = serverRepository.changePhotoLocation(
                    photo = photo,
                    newUserOwnerId = userId.takeUnless { makePublic },
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

    fun updateCaption(photo: NetworkPhoto, newCaption: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            serverRepository.updateCaption(photo, newCaption)
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
            photos.parallelStream().map { photo ->
                async {
                    if (serverRepository.deleteNetworkPhoto(photo.ownerUserId, photo.id)) {
                        photosRepository.removeNetworkReference(photo)
                    }
                }
            }.toList().map { it.await() }
        }
    }

    fun deleteLocalPhotos(activity: Activity, photos: List<LocalPhoto>) {
        val pendingIntent = MediaStore.createTrashRequest(
            activity.contentResolver,
            photos.map { it.uri },
            true
        )

        activity.startIntentSenderForResult(pendingIntent.intentSender, 12345, null, 0, 0, 0)
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

    companion object {
        private const val UNIQUE_PERIODIC_UPLOAD = "periodic_upload"

        private val currentDate = Clock.System.now().toLocalDateTime(TIME_ZONE)

        private fun Flow<PagingData<NetworkPhoto>>.mapPagingPhotos() = map { pagingData ->
            pagingData
                .insertSeparators { before, after ->
                    after ?: return@insertSeparators null

                    val beforeDate = before?.let {
                        val instant = Instant.fromEpochSeconds(it.timeCreated)
                        instant.toLocalDateTime(TIME_ZONE)
                    }

                    val afterDate =
                        Instant.fromEpochSeconds(after.timeCreated).toLocalDateTime(TIME_ZONE)

                    if (beforeDate == null
                        || beforeDate.monthNumber != afterDate.monthNumber
                        || beforeDate.year != afterDate.year
                    ) {
                        buildDateString(afterDate)
                    } else null
                }
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
