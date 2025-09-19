package net.theluckycoder.familyphotos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.model.db.NetworkFolder
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import net.theluckycoder.familyphotos.workers.UploadWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UploadPhotosViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val foldersRepository: FoldersRepository,
    private val serverRepository: ServerRepository,
    settingsStore: SettingsDataStore,
    private val workManager: WorkManager,
) : ViewModel() {

    val networkFolders = settingsStore.showFoldersAscending
        .flatMapLatest { foldersRepository.networkFoldersFlow(it) }

    fun getLocalPhotos(photoIds: LongArray): Deferred<List<LocalPhoto>> =
        viewModelScope.async(Dispatchers.IO) {
            photoIds.map { photosRepository.getLocalPhoto(it) }.filterNotNull()
        }

    fun getNetworkPhotos(photoIds: LongArray): Deferred<List<NetworkPhoto>> =
        viewModelScope.async(Dispatchers.IO) {
            photoIds.map { photosRepository.getNetworkPhoto(it) }.filterNotNull()
        }

    /**
     * Receives a list of [LocalPhoto] ids that will be uploaded
     */
    fun uploadPhotosAsync(
        localPhotos: LongArray,
        makePublic: Boolean,
        uploadFolder: String?
    ): Operation {
        val data = Data.Builder()
            .putAll(
                mapOf(
                    UploadWorker.KEY_INPUT_LIST to localPhotos,
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

    /**
     * Change the folder and the user where these [NetworkPhoto]s belong
     *
     * @returns true if all photos have been successfully moved
     */
    fun movePhotos(
        networkPhotos: LongArray,
        makePublic: Boolean,
        newFolderName: String?
    ): Deferred<Boolean> = viewModelScope.async(Dispatchers.IO) {
        val photos = networkPhotos.map { id ->
            photosRepository.getNetworkPhotoFlow(id).firstOrNull()
        }.filterNotNull()

        val result = serverRepository.movePhotos(
            photos = photos,
            makePublic = makePublic,
            newFolderName = newFolderName
        )

        Log.d("Moving Photos", "Moved $photos result=$result")

        result
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
}