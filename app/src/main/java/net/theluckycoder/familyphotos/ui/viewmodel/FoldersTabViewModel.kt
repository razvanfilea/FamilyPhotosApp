package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.model.NetworkFolder
import net.theluckycoder.familyphotos.core.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.workers.BackupAndUploadWorker
import net.theluckycoder.familyphotos.workers.enqueueBackupAndUploadWorker
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoldersTabViewModel @Inject constructor(
    private val foldersRepository: FoldersRepository,
    userDataStore: UserDataStore,
    settingsStore: SettingsDataStore,
    private val workManager: WorkManager,
) : ViewModel() {

    data class BackupProgress(
        val current: Int,
        val total: Int,
        val currentPhotoPercent: Int = 0
    )

    val activeFolderViewModel = MutableStateFlow<FolderScreenViewModel?>(null)

    fun registerFolderViewModel(viewModel: FolderScreenViewModel) {
        activeFolderViewModel.value = viewModel
        viewModel.onClearedCallback = {
            if (activeFolderViewModel.value == viewModel) {
                activeFolderViewModel.value = null
            }
        }
    }

    val localFolders = settingsStore.showFoldersAscending
        .flatMapLatest { foldersRepository.localFoldersFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val networkFolders: StateFlow<List<NetworkFolder>> = settingsStore.photoType
        .combine(settingsStore.showFoldersAscending) { type, ascending -> type to ascending }
        .flatMapLatest { (type, ascending) ->
            foldersRepository.networkFoldersFlow(type, ascending)
        }.map {
            it
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val currentUser = userDataStore.user

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
                        total = info.progress.getInt(BackupAndUploadWorker.KEY_PROGRESS_TOTAL, 0),
                        currentPhotoPercent = info.progress.getInt(BackupAndUploadWorker.KEY_PROGRESS_PHOTO_PERCENT, 0)
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun refreshLocalPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            foldersRepository.updatePhoneAlbums()
        }
    }

    fun triggerBackup() {
        workManager.enqueueBackupAndUploadWorker(
            skipFolderScan = false
        )
    }
}