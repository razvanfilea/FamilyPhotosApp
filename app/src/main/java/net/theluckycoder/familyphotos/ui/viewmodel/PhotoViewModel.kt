package net.theluckycoder.familyphotos.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.repository.FoldersRepository
import net.theluckycoder.familyphotos.repository.PhotosRepository
import net.theluckycoder.familyphotos.repository.ServerRepository
import javax.inject.Inject

@HiltViewModel
class PhotoViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
    private val foldersRepository: FoldersRepository,
) : ViewModel() {

    fun getNetworkPhotoFlow(photoId: Long): Flow<NetworkPhoto?> =
        photosRepository.getNetworkPhoto(photoId)

    fun getPhotosInMonth(photo: Photo) = photosRepository.getPhotosInProximity(photo)

    fun getPhotosInWeek(photo: NetworkPhoto) = photosRepository.getMemories(photo.timeCreated, photo.ownerUserId)

    fun getPhotoLocalUriAsync(photo: Photo): Deferred<Uri?> = viewModelScope.async(Dispatchers.IO) {
        when (photo) {
            is LocalPhoto -> photo.uri
            is NetworkPhoto -> {
                val localPhoto = photosRepository.getLocalPhotoFromNetwork(photo.id)
                    ?: serverRepository.saveNetworkPhotoToStorage(photo)

                localPhoto?.uri
            }
        }
    }

    fun getNetworkFolderPhotos(folder: String) =
        foldersRepository.networkPhotosFromFolder(folder)

    fun getLocalFolderPhotos(folder: String) =
        foldersRepository.localPhotosFromFolder(folder)
}