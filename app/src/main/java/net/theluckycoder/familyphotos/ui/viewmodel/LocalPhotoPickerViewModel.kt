package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.core.data.model.FolderSortOrder
import net.theluckycoder.familyphotos.core.data.model.LocalFolder
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.model.TimelineLayout
import net.theluckycoder.familyphotos.core.data.model.db.UploadQueueEntry
import net.theluckycoder.familyphotos.core.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotoUploadRepository
import net.theluckycoder.familyphotos.ui.SnackbarManager
import net.theluckycoder.familyphotos.workers.enqueueBackupAndUploadWorker
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalPhotoPickerViewModel @Inject constructor(
    private val foldersRepository: FoldersRepository,
    private val photoUploadRepository: PhotoUploadRepository,
    private val settingsDataStore: SettingsDataStore,
    private val workManager: WorkManager,
    private val snackbarManager: SnackbarManager,
) : ViewModel() {

    val localFolders: StateFlow<List<LocalFolder>> = settingsDataStore.localFolderSortOrder
        .flatMapLatest { sortOrder -> foldersRepository.localFoldersFlow(sortOrder) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localFolderSortOrder: StateFlow<FolderSortOrder> = settingsDataStore.localFolderSortOrder

    fun setLocalFolderSortOrder(sortOrder: FolderSortOrder) {
        viewModelScope.launch {
            settingsDataStore.setLocalFolderSortOrder(sortOrder)
        }
    }

    fun getFolderPhotosPager(folderName: String): Flow<PagingData<LocalPhoto>> = Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = true)
    ) {
        foldersRepository.localPhotosFromFolderPaged(folderName)
    }.flow.cachedIn(viewModelScope)

    fun getFolderTimelineLayout(folderName: String): StateFlow<TimelineLayout> =
        foldersRepository.localMonthSummariesForFolder(folderName)
            .map { TimelineLayout.build(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimelineLayout.EMPTY)

    fun uploadPhotos(selectedPhotoIds: Set<Long>, targetFolderId: Long, targetFolderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = selectedPhotoIds.map { localPhotoId ->
                UploadQueueEntry(
                    localPhotoId = localPhotoId,
                    makePublic = null,
                    folderId = targetFolderId,
                    newFolderName = null,
                    isManualUpload = true,
                )
            }
            photoUploadRepository.enqueueUploads(entries)
            workManager.enqueueBackupAndUploadWorker(skipFolderScan = true)
            snackbarManager.showMessage(R.string.status_upload_queued)
        }
    }
}
