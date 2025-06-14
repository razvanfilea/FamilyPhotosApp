package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.data.model.ExifData
import net.theluckycoder.familyphotos.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import javax.inject.Inject

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    fun getNetworkPhotoFlow(photoId: Long): Flow<NetworkPhoto?> =
        photosRepository.getNetworkPhotoFlow(photoId)

    fun updateFavorite(photo: NetworkPhoto, add: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            serverRepository.updateFavorite(photo, add)
        }
    }

    suspend fun getExifData(photo: NetworkPhoto): ExifData? = withContext(Dispatchers.IO) {
        serverRepository.getExifData(photo)
    }
}