package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.model.NetworkFolder
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.UploadChoice
import net.theluckycoder.familyphotos.core.data.model.db.UploadQueueEntry
import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import net.theluckycoder.familyphotos.core.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotoUploadRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.core.data.repository.ServerRepository
import net.theluckycoder.familyphotos.ui.SnackbarManager
import net.theluckycoder.familyphotos.ui.UiMessageType
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
    userDataStore: UserDataStore,
    private val workManager: WorkManager,
    private val snackbarManager: SnackbarManager,
) : ViewModel() {

    val currentUser: StateFlow<UserDto?> = userDataStore.user

    val networkFolders: StateFlow<List<NetworkFolder>> =
        settingsStore.photoType.combine(settingsStore.showFoldersAscending) { type, ascending -> type to ascending }
            .flatMapLatest { (type, ascending) ->
                foldersRepository.networkFoldersFlow(type, ascending)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

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
    fun uploadPhotosAsync(localPhotos: LongArray, uploadChoice: UploadChoice) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = localPhotos.map { localPhotoId ->
                UploadQueueEntry(
                    localPhotoId = localPhotoId,
                    makePublic = uploadChoice.isPublic,
                    folderId = (uploadChoice as? UploadChoice.Folder)?.folderId,
                    newFolderName = (uploadChoice as? UploadChoice.NewFolder)?.name,
                    isManualUpload = true,
                )
            }
            photoUploadRepository.enqueueUploads(entries)
            workManager.enqueueBackupAndUploadWorker(
                skipFolderScan = true
            )
            snackbarManager.showMessage(R.string.status_upload_queued)
        }
    }

    fun movePhotos(networkPhotos: LongArray, uploadChoice: UploadChoice) {
        viewModelScope.launch(Dispatchers.IO) {
            val photos = networkPhotos.map { id ->
                photosRepository.getNetworkPhoto(id)
            }.filterNotNull()

            val result = serverRepository.movePhotos(photos = photos, uploadChoice = uploadChoice)
            if (result) {
                snackbarManager.showPluralMessage(R.plurals.status_move_success, photos.size, UiMessageType.Success)
            } else {
                snackbarManager.showPluralMessage(R.plurals.status_move_failure, photos.size, UiMessageType.Error)
            }
        }
    }

}
