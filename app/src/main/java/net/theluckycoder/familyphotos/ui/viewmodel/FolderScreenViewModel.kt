package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.model.SharedFolderAccess
import net.theluckycoder.familyphotos.core.data.model.TimelineLayout
import net.theluckycoder.familyphotos.core.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotoUploadRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.core.data.repository.ServerRepository
import net.theluckycoder.familyphotos.core.data.repository.SharingRepository
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.SnackbarManager
import net.theluckycoder.familyphotos.ui.UiMessageType
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel.Companion.PAGING_CONFIG
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FolderScreenViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val foldersRepository: FoldersRepository,
    private val photoUploadRepository: PhotoUploadRepository,
    private val serverRepository: ServerRepository,
    private val sharingRepository: SharingRepository,
    userDataStore: UserDataStore,
    private val snackbarManager: SnackbarManager,
) : ViewModel() {

    val currentUser = userDataStore.user

    var onClearedCallback: (() -> Unit)? = null

    val photoListState = MutableStateFlow(LazyGridState())

    private val _source = MutableStateFlow<FolderNav.Source?>(null)
    val source = _source.asStateFlow()

    val networkFolder = _source.flatMapLatest { source ->
        if (source is FolderNav.Source.Network) {
            foldersRepository.getFolderFlow(source.folderId)
        } else {
            flowOf(null)
        }
    }

    val networkFolderPhotosPager = _source
        .flatMapLatest { source ->
            if (source is FolderNav.Source.Network) {
                Pager(PAGING_CONFIG) {
                    foldersRepository.networkPhotosFromFolderPaged(source.folderId)
                }.flow
            } else {
                emptyFlow()
            }
        }
        .cachedIn(viewModelScope)

    val localFolderPhotosPager = _source
        .flatMapLatest { source ->
            if (source is FolderNav.Source.Local) {
                Pager(PAGING_CONFIG) {
                    foldersRepository.localPhotosFromFolderPaged(source.name)
                }.flow
            } else {
                emptyFlow()
            }
        }
        .cachedIn(viewModelScope)

    val favoritePhotosPager = _source
        .flatMapLatest { source ->
            if (source is FolderNav.Source.Favorites) {
                Pager(PAGING_CONFIG) {
                    photosRepository.getFavoritePhotosPaged()
                }.flow
            } else {
                emptyFlow()
            }
        }
        .cachedIn(viewModelScope)

    val favoriteTimelineLayout: StateFlow<TimelineLayout> = photosRepository.getFavoriteMonthSummaries()
        .map { summaries -> TimelineLayout.build(summaries) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), TimelineLayout.EMPTY)

    val networkFolderTimelineLayout: StateFlow<TimelineLayout> = _source
        .flatMapLatest { source ->
            if (source is FolderNav.Source.Network) {
                foldersRepository.networkMonthSummariesForFolder(source.folderId)
            } else {
                flowOf(emptyList())
            }
        }
        .map { summaries -> TimelineLayout.build(summaries) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), TimelineLayout.EMPTY)

    val localFolderTimelineLayout: StateFlow<TimelineLayout> = _source
        .flatMapLatest { source ->
            if (source is FolderNav.Source.Local) {
                foldersRepository.localMonthSummariesForFolder(source.name)
            } else {
                flowOf(emptyList())
            }
        }
        .map { summaries -> TimelineLayout.build(summaries) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), TimelineLayout.EMPTY)

    fun setSource(source: FolderNav.Source) {
        if (_source.value != source) {
            photoListState.value = LazyGridState()
            _source.value = source
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
                snackbarManager.showMessage(R.string.status_member_added, UiMessageType.Success)
            } else {
                snackbarManager.showMessage(R.string.error_member_add_failed, UiMessageType.Error)
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
                snackbarManager.showMessage(R.string.status_permissions_updated, UiMessageType.Success)
            } else {
                snackbarManager.showMessage(R.string.error_permissions_update_failed, UiMessageType.Error)
            }
        }
    }

    fun removeMemberFromFolder(shareId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = sharingRepository.revokeShare(shareId = shareId)
            if (result) {
                _sharingRefreshTrigger.update { it + 1 }
                snackbarManager.showMessage(R.string.status_member_removed, UiMessageType.Success)
            } else {
                snackbarManager.showMessage(R.string.error_member_remove_failed, UiMessageType.Error)
            }
        }
    }

    fun renameFolder(folderId: Long, newName: String, isPublic: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = serverRepository.renameFolder(folderId, newName, isPublic)
            if (result) {
                snackbarManager.showMessage(R.string.status_folder_renamed, UiMessageType.Success)
            } else {
                snackbarManager.showMessage(R.string.error_folder_rename_failed, UiMessageType.Error)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        onClearedCallback?.invoke()
    }
}
