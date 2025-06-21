package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.data.local.db.LocalFolderBackupDao
import net.theluckycoder.familyphotos.data.model.LocalFolderToBackup
import net.theluckycoder.familyphotos.data.model.LocalPhoto
import net.theluckycoder.familyphotos.data.model.NetworkFolder
import net.theluckycoder.familyphotos.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel.Companion.PAGING_CONFIG
import javax.inject.Inject


@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
    private val foldersRepository: FoldersRepository,
    private val foldersToBackupDao: LocalFolderBackupDao,
    private val settingsStore: SettingsDataStore,
) : ViewModel() {

    val selectedPhotoType = settingsStore.photoType.stateIn(
        viewModelScope, SharingStarted.Lazily, PhotoType.All
    )

    val showFoldersAscending = settingsStore.showFoldersAscending.stateIn(
        viewModelScope,
        SharingStarted.Lazily, true
    )

    val favoritePhotosPager = Pager(PAGING_CONFIG) {
        photosRepository.getFavoritePhotosPaged()
    }.flow.cachedIn(viewModelScope)
    val localFolders = showFoldersAscending
        .flatMapLatest { foldersRepository.localFoldersFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val networkFolders = showFoldersAscending
        .flatMapLatest { foldersRepository.networkFoldersFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val photoListState = MutableStateFlow(LazyGridState())

    private val _selectedNetworkFolder = MutableStateFlow<String?>(null)
    val networkFolderPhotosPager: Flow<PagingData<NetworkPhoto>> = _selectedNetworkFolder
        .flatMapLatest { folderName ->
            if (folderName != null) {
                Pager(PAGING_CONFIG) {
                    foldersRepository.networkPhotosFromFolderPaged(folderName)
                }.flow.cachedIn(viewModelScope)
            } else {
                emptyFlow()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PagingData.empty())

    private val _selectedLocalFolder = MutableStateFlow<String?>(null)
    val localFolderPhotosPager: Flow<PagingData<LocalPhoto>> = _selectedLocalFolder
        .flatMapLatest { folderName ->
            if (folderName != null) {
                Pager(PAGING_CONFIG) {
                    foldersRepository.localPhotosFromFolderPaged(folderName)
                }.flow.cachedIn(viewModelScope)
            } else {
                emptyFlow()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PagingData.empty())

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
        foldersToBackupDao.getAll()
            .map { it.firstOrNull { folder -> folder == folderName } != null }

    fun backupLocalFolder(folder: String, add: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (add) {
                foldersToBackupDao.insert(LocalFolderToBackup(folder))
            } else {
                foldersToBackupDao.delete(LocalFolderToBackup(folder))
            }
        }
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