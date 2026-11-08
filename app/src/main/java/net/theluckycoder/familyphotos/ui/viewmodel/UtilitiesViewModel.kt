package net.theluckycoder.familyphotos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.db.PhotoStatistics
import net.theluckycoder.familyphotos.core.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.core.data.repository.ServerRepository
import net.theluckycoder.familyphotos.ui.SnackbarManager
import net.theluckycoder.familyphotos.ui.UiMessageType
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class UtilitiesViewModel @Inject constructor(
    photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
    private val foldersRepository: FoldersRepository,
    private val snackbarManager: SnackbarManager,
) : ViewModel() {

    val photoStatistics = photosRepository.getPhotoStatistics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PhotoStatistics.Empty)

    val largePhotos = photosRepository.getLargePhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _duplicates = MutableStateFlow<DuplicatesState>(DuplicatesState.Loading)
    val duplicates: StateFlow<DuplicatesState> = _duplicates.asStateFlow()

    fun loadDuplicates() {
        _duplicates.value = DuplicatesState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val groups = try {
                serverRepository.getDuplicates()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load duplicates", e)
                null
            }
            _duplicates.value = if (groups == null) {
                DuplicatesState.Error
            } else {
                val folderIds = groups.flatMap { g -> g.mapNotNull { it.folderId } }
                DuplicatesState.Loaded(groups, foldersRepository.getFolderNames(folderIds))
            }
        }
    }

    /** Moves the photos to the trash, or restores them, in one request. */
    suspend fun trashPhotos(photoIds: List<Long>, trash: Boolean = true): Boolean {
        val success = try {
            withContext(Dispatchers.IO) { serverRepository.trashNetworkPhoto(photoIds.toLongArray(), trash) }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to trash photos ($trash)", e)
            false
        }
        if (!success) {
            snackbarManager.showMessage(
                if (trash) R.string.error_trash_failed else R.string.error_restore_failed,
                UiMessageType.Error
            )
        }
        return success
    }

    fun showTrashed(count: Int) {
        snackbarManager.showPluralMessage(R.plurals.status_photos_trashed, count)
    }

    /** Takes the groups that contain any of the [photoIds] off the list. */
    fun removeDuplicateGroups(photoIds: Set<Long>) {
        _duplicates.update { state ->
            if (state !is DuplicatesState.Loaded) state
            else state.copy(groups = state.groups.filter { g -> g.none { it.id in photoIds } })
        }
    }

    fun restoreDuplicateGroup(index: Int, group: List<NetworkPhoto>) {
        _duplicates.update { state ->
            if (state !is DuplicatesState.Loaded) state
            else state.copy(
                groups = state.groups.toMutableList().apply { add(index.coerceIn(0, size), group) }
            )
        }
    }

    private companion object {
        const val TAG = "UtilitiesViewModel"
    }
}

sealed interface DuplicatesState {
    data object Loading : DuplicatesState
    data object Error : DuplicatesState
    data class Loaded(
        val groups: List<List<NetworkPhoto>>,
        val folderNames: Map<Long, String>,
    ) : DuplicatesState
}
