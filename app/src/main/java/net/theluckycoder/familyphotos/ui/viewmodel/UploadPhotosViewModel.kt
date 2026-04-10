package net.theluckycoder.familyphotos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.model.db.NetworkFolder
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.db.UploadQueueEntry
import net.theluckycoder.familyphotos.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.data.repository.PhotoUploadRepository
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import net.theluckycoder.familyphotos.workers.enqueueBackupAndUploadWorker
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UploadPhotosViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val foldersRepository: FoldersRepository,
    private val serverRepository: ServerRepository,
    private val photoUploadRepository: PhotoUploadRepository,
    settingsStore: SettingsDataStore,
    private val workManager: WorkManager,
) : ViewModel() {

    val networkFolders = settingsStore.showFoldersAscending
        .flatMapLatest { foldersRepository.networkFoldersFlow(PhotoType.All, it) }

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
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = localPhotos.map { localPhotoId ->
                UploadQueueEntry(
                    localPhotoId = localPhotoId,
                    makePublic = makePublic,
                    uploadFolder = uploadFolder,
                    isManualUpload = true,
                )
            }
            photoUploadRepository.enqueueUploads(entries)
            workManager.enqueueBackupAndUploadWorker(skipFolderScan = true)
        }
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
