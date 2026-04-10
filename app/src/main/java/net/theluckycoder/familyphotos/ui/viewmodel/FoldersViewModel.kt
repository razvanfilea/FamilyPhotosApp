package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.cachedIn
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.model.db.MonthSummary
import net.theluckycoder.familyphotos.data.model.db.NetworkFolder
import net.theluckycoder.familyphotos.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.data.repository.PhotoUploadRepository
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel.Companion.PAGING_CONFIG
import net.theluckycoder.familyphotos.utils.mapPagingPhotos
import net.theluckycoder.familyphotos.workers.enqueueBackupAndUploadWorker
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val foldersRepository: FoldersRepository,
    private val photoUploadRepository: PhotoUploadRepository,
    private val settingsStore: SettingsDataStore,
    private val workManager: WorkManager,
) : ViewModel() {

    val selectedPhotoType = settingsStore.photoType.stateIn(
        viewModelScope, SharingStarted.Lazily, PhotoType.All
    )

    val showFoldersAscending = settingsStore.showFoldersAscending.stateIn(
        viewModelScope,
        SharingStarted.Lazily, true
    )
    val showFoldersAsGrid = settingsStore.showFoldersAsGrid.stateIn(
        viewModelScope,
        SharingStarted.Lazily, true
    )

    val favoritePhotosPager = Pager(PAGING_CONFIG) {
        photosRepository.getFavoritePhotosPaged()
    }.flow.mapPagingPhotos().cachedIn(viewModelScope)

    val localFolders = showFoldersAscending
        .flatMapLatest { foldersRepository.localFoldersFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val networkFolders: StateFlow<List<NetworkFolder>> = selectedPhotoType
        .combine(showFoldersAscending) { type, ascending -> type to ascending }
        .flatMapLatest { (type, ascending) ->
            foldersRepository.networkFoldersFlow(type, ascending)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val photoListState = MutableStateFlow(LazyGridState())

    private val _selectedNetworkFolder = MutableStateFlow<String?>(null)
    val networkFolderPhotosPager = _selectedNetworkFolder
        .combine(selectedPhotoType) { folderName, photoType -> folderName to photoType }
        .flatMapLatest { (folderName, photoType) ->
            if (folderName != null) {
                Pager(PAGING_CONFIG) {
                    foldersRepository.networkPhotosFromFolderPaged(folderName, photoType)
                }.flow
            } else {
                emptyFlow()
            }
        }
        .mapPagingPhotos()
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
        .mapPagingPhotos()
        .cachedIn(viewModelScope)

    val networkFolderMonthSummaries: StateFlow<List<MonthSummary>> = _selectedNetworkFolder
        .combine(selectedPhotoType) { folderName, photoType -> folderName to photoType }
        .flatMapLatest { (folderName, photoType) ->
            if (folderName != null) {
                foldersRepository.networkMonthSummariesForFolder(folderName, photoType)
            } else {
                flowOf(emptyList())
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val localFolderMonthSummaries: StateFlow<List<MonthSummary>> = _selectedLocalFolder
        .flatMapLatest { folderName ->
            if (folderName != null) {
                foldersRepository.localMonthSummariesForFolder(folderName)
            } else {
                flowOf(emptyList())
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun refreshLocalPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            foldersRepository.updatePhoneAlbums()
        }
    }

    fun setSelectedPhotoType(photoType: PhotoType) {
        viewModelScope.launch(Dispatchers.Default) {
            settingsStore.setSelectedPhotoType(photoType)
        }
    }

    fun setShowFoldersAscending(ascending: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setShowFoldersAscending(ascending)
        }
    }

    fun setShowAsGrid(asGrid: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.setShowFoldersAsGrid(asGrid)
        }
    }

    fun loadNetworkFolderPhotos(folderName: String) {
        if (_selectedNetworkFolder.value != folderName) {
            photoListState.value = LazyGridState()
            _selectedNetworkFolder.value = folderName
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
                workManager.enqueueBackupAndUploadWorker(skipFolderScan = false)
            } else {
                foldersRepository.removeBackupFolder(folder)
                photoUploadRepository.removeFromQueueByFolder(folder)
            }
        }
    }
}