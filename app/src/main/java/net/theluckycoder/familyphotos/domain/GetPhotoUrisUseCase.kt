package net.theluckycoder.familyphotos.domain

import android.net.Uri
import dagger.hilt.android.scopes.ViewModelScoped
import net.theluckycoder.familyphotos.data.repository.MediaStoreRepository
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import javax.inject.Inject

/**
 * Use case for retrieving photo URIs for sharing.
 * Handles both network and local photos.
 */
@ViewModelScoped
class GetPhotoUrisUseCase @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val mediaStoreRepository: MediaStoreRepository,
) {
    /**
     * Gets URIs for network photos, downloading them to local storage if needed.
     */
    suspend fun getNetworkPhotoUris(photoIds: LongArray): List<Uri> {
        return photoIds.map { photoId ->
            val photo = photosRepository.getNetworkPhoto(photoId) ?: return@map null
            val localPhoto = photosRepository.getLocalPhotoFromNetwork(photo.id)
                ?: mediaStoreRepository.saveNetworkPhotoToStorage(photo)

            localPhoto?.uri
        }.filterNotNull()
    }

    /**
     * Gets URIs for local photos.
     */
    suspend fun getLocalPhotoUris(photoIds: LongArray): List<Uri> {
        return photoIds.map { photosRepository.getLocalPhoto(it)?.uri }.filterNotNull()
    }
}
