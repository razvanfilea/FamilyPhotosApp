package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.cachedIn
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.model.TimelineLayout
import net.theluckycoder.familyphotos.core.data.model.NetworkFolder
import net.theluckycoder.familyphotos.core.data.model.SharedFolderAccess
import net.theluckycoder.familyphotos.core.data.model.network.UserDto
import net.theluckycoder.familyphotos.core.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotoUploadRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.core.data.repository.SharingRepository
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel.Companion.PAGING_CONFIG
import net.theluckycoder.familyphotos.workers.BackupAndUploadWorker
import net.theluckycoder.familyphotos.workers.enqueueBackupAndUploadWorker
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val foldersRepository: FoldersRepository,
    private val photoUploadRepository: PhotoUploadRepository,
    private val sharingRepository: SharingRepository,
    userDataStore: UserDataStore,
    settingsStore: SettingsDataStore,
    private val workManager: WorkManager,
) : ViewModel() {

    data class BackupProgress(
        val current: Int,
        val total: Int
    )

    val favoritePhotosPager = Pager(PAGING_CONFIG) {
        photosRepository.getFavoritePhotosPaged()
    }.flow.cachedIn(viewModelScope)

    val localFolders = settingsStore.showFoldersAscending
        .flatMapLatest { foldersRepository.localFoldersFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val networkFolders: StateFlow<List<NetworkFolder>> = settingsStore.photoType
        .combine(settingsStore.showFoldersAscending) { type, ascending -> type to ascending }
        .flatMapLatest { (type, ascending) ->
            foldersRepository.networkFoldersFlow(type, ascending)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val photoListState = MutableStateFlow(LazyGridState())

    val currentUser =
        userDataStore.userId.combine(userDataStore.displayName) { userId, displayName ->
            UserDto(
                userId ?: "",
                displayName
            )
        }

    private val _selectedNetworkFolder = MutableStateFlow<Long?>(null)
    val networkFolder = _selectedNetworkFolder.flatMapLatest { folderId ->
        if (folderId == null) flowOf(null) else foldersRepository.getFolderFlow(folderId)
    }
    val networkFolderPhotosPager = _selectedNetworkFolder
        .flatMapLatest { folderId ->
            if (folderId != null) {
                Pager(PAGING_CONFIG) {
                    foldersRepository.networkPhotosFromFolderPaged(folderId)
                }.flow
            } else {
                emptyFlow()
            }
        }
        .cachedIn(viewModelScope)

    private val _selectedLocalFolder = MutableStateFlow<String?>(null)
    val localFolderPhotosPager = _selectedLocalFolder
        .flatMapLatest { folderName ->
            if (folderName != null) {
                Pager(PAGING_CONFIG) {
                    foldersRepository.localPhotosFromFolderPaged(folderName)
                }.flow
            } else {
                emptyFlow()
            }
        }
        .cachedIn(viewModelScope)

    val networkFolderTimelineLayout: StateFlow<TimelineLayout> = _selectedNetworkFolder
        .flatMapLatest { folderId ->
            if (folderId != null) {
                foldersRepository.networkMonthSummariesForFolder(folderId)
            } else {
                flowOf(emptyList())
            }
        }
        .map { summaries -> TimelineLayout.build(summaries) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), TimelineLayout.EMPTY)

    val localFolderTimelineLayout: StateFlow<TimelineLayout> = _selectedLocalFolder
        .flatMapLatest { folderName ->
            if (folderName != null) {
                foldersRepository.localMonthSummariesForFolder(folderName)
            } else {
                flowOf(emptyList())
            }
        }
        .map { summaries -> TimelineLayout.build(summaries) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), TimelineLayout.EMPTY)

    val backupFolders: StateFlow<Set<String>> = foldersRepository.getBackupFolders()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    val pendingBackupCount: StateFlow<Int> = foldersRepository.getPendingBackupCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    val backupProgress: StateFlow<BackupProgress?> =
        workManager.getWorkInfosByTagFlow(BackupAndUploadWorker.TAG)
            .map { workInfos ->
                workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.let { info ->
                    BackupProgress(
                        current = info.progress.getInt(
                            BackupAndUploadWorker.KEY_PROGRESS_CURRENT,
                            0
                        ),
                        total = info.progress.getInt(BackupAndUploadWorker.KEY_PROGRESS_TOTAL, 0)
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun refreshLocalPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            foldersRepository.updatePhoneAlbums()
        }
    }

    fun loadNetworkFolderPhotos(folderId: Long) {
        if (_selectedNetworkFolder.value != folderId) {
            photoListState.value = LazyGridState()
            _selectedNetworkFolder.value = folderId
        }
    }

    fun loadLocalFolderPhotos(folderName: String) {
        if (_selectedLocalFolder.value != folderName) {
            photoListState.value = LazyGridState()
            _selectedLocalFolder.value = folderName
        }
    }

    fun isLocalFolderBackupUp(folderName: String): Flow<Boolean> =
        foldersRepository.getBackupFolders()
            .map { it.firstOrNull { folder -> folder == folderName } != null }

    fun backupLocalFolder(folder: String, add: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (add) {
                foldersRepository.addBackupFolder(folder)
            } else {
                foldersRepository.removeBackupFolder(folder)
                photoUploadRepository.removeFromQueueByFolder(folder)
            }
        }
    }

    fun triggerBackup() {
        workManager.enqueueBackupAndUploadWorker(
            skipFolderScan = false
        )
    }

    private val _sharingRefreshTrigger = MutableStateFlow(0)

    fun getFolderShares(folderId: Long): Flow<SharedFolderAccess> =
        _sharingRefreshTrigger.flatMapLatest {
            flow { emit(sharingRepository.getFolderShares(folderId)) }
        }.flowOn(Dispatchers.IO)

    fun addMemberToFolder(folderId: Long, userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = sharingRepository.createShare(folderId = folderId, granteeId = userId)
            if (result != null) {
                _sharingRefreshTrigger.update { it + 1 }
            }
        }
    }

    fun updateMemberFolderPermissions(shareId: Long, canUpload: Boolean, canDelete: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = sharingRepository.updateShare(
                shareId = shareId,
                canUpload = canUpload,
                canDelete = canDelete
            )
            if (result != null) {
                _sharingRefreshTrigger.update { it + 1 }
            }
        }
    }

    fun removeMemberFromFolder(shareId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = sharingRepository.revokeShare(shareId = shareId)
            if (result) {
                _sharingRefreshTrigger.update { it + 1 }
            }
        }
    }

}