package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.core.data.repository.ServerRepository
import net.theluckycoder.familyphotos.ui.SnackbarManager
import net.theluckycoder.familyphotos.ui.UiMessageType
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
    private val snackbarManager: SnackbarManager,
) : ViewModel() {

    val trashedPhotos = photosRepository.getTrashedPhotos()

    fun deleteNetworkPhotos(photoIds: LongArray) {
        viewModelScope.launch(Dispatchers.IO) {
            photoIds.map { photoId ->
                async {
                    val networkPhoto = photosRepository.getNetworkPhoto(photoId)
                    if (networkPhoto != null && serverRepository.deleteNetworkPhoto(photoId)) {
                        photosRepository.removeNetworkReference(networkPhoto)
                        true
                    } else {
                        false
                    }
                }
            }.awaitAll()
            snackbarManager.showPluralMessage(R.plurals.status_photos_deleted, photoIds.size)
        }
    }

    fun restorePhotos(photoIds: LongArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = serverRepository.trashNetworkPhoto(photoIds, false)
            if (result) {
                snackbarManager.showPluralMessage(R.plurals.status_photos_restored, photoIds.size, UiMessageType.Success)
            } else {
                snackbarManager.showMessage(R.string.error_restore_failed, UiMessageType.Error)
            }
        }
    }
}