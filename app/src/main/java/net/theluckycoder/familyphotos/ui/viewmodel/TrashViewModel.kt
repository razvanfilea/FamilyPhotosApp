package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    val trashedPhotos = photosRepository.getTrashedPhotos()

    suspend fun deleteNetworkPhotos(photoIds: LongArray) = withContext(Dispatchers.IO) {
        photoIds.map { photoId ->
            async {
                val networkPhoto = photosRepository.getNetworkPhoto(photoId)
                if (networkPhoto != null && serverRepository.deleteNetworkPhoto(photoId)) {
                    photosRepository.removeNetworkReference(networkPhoto)
                }
            }
        }
    }.toList().awaitAll()

    fun restorePhotos(photoIds: LongArray) = viewModelScope.launch(Dispatchers.IO) {
        serverRepository.trashNetworkPhoto(photoIds, false)
    }
}