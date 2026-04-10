package net.theluckycoder.familyphotos.data.repository

import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.theluckycoder.familyphotos.data.local.db.FavoritePhotosDao
import net.theluckycoder.familyphotos.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.data.model.ExifData
import net.theluckycoder.familyphotos.data.model.db.NetworkFolder
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.db.isPublic
import net.theluckycoder.familyphotos.data.remote.PhotosService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles network API operations for photos.
 * MediaStore operations are in [MediaStoreRepository].
 * Upload operations are in [PhotoUploadRepository].
 */
@Singleton
class ServerRepository @Inject constructor(
    private val photosService: Lazy<PhotosService>,
    private val networkPhotosDao: NetworkPhotosDao,
    private val favoritePhotosDao: FavoritePhotosDao,
) {

    enum class DownloadResponse {
        SUCCESSFUL,
        UNSUCCESSFUL,
        NOT_LOGGED_IN,
        FULL_DOWNLOAD_NEEDED
    }

    suspend fun downloadPartialPhotos(): DownloadResponse {
        val lastSyncedEventLogId = networkPhotosDao.getEventLogId()
        Log.i("ServerRepository", "Last synced event id: $lastSyncedEventLogId")

        val response = photosService.get().getEventLogsList(lastSyncedEventLogId)

        if (!response.isSuccessful) {
            return when (response.code()) {
                401 -> DownloadResponse.NOT_LOGGED_IN // UNAUTHORIZED
                409 -> DownloadResponse.FULL_DOWNLOAD_NEEDED // CONFLICT
                else -> DownloadResponse.UNSUCCESSFUL
            }
        } else {
            val partialPhotos = response.body()!!
            if (partialPhotos.events.isNotEmpty()) {
                networkPhotosDao.updatePartials(partialPhotos.events, partialPhotos.eventLogId)
            }

            Log.i(
                "ServerRepository",
                "Downloaded partial photos. EventLogId: ${partialPhotos.eventLogId}"
            )

            return DownloadResponse.SUCCESSFUL
        }
    }

    suspend fun downloadAllPhotos(): DownloadResponse = coroutineScope {
        val service = photosService.get()
        val photosAsync = async { service.getFullPhotosList() }

        val favoritePhotosResponse = service.getFavorites()
        favoritePhotosResponse.errorBody()
            ?.let { Log.e("ServerRepository", "Failed to getOrCompute favorites ${it.string()}") }

        val photosResponse = photosAsync.await()

        if (photosResponse.isSuccessful) {
            val fullPhotos = photosResponse.body()!!

            if (fullPhotos.photos.isNotEmpty()) {
                networkPhotosDao.replaceAll(fullPhotos.photos, fullPhotos.eventLogId)
                Log.i(
                    "ServerRepository",
                    "Downloaded all server photos. EventLogId: ${fullPhotos.eventLogId}"
                )

                // Only insert favorite photos after downloading all photos
                favoritePhotosResponse.body()?.let { favoritePhotos ->
                    favoritePhotosDao.replaceAll(favoritePhotos)
                }

                return@coroutineScope DownloadResponse.SUCCESSFUL
            }
        }

        photosResponse.errorBody()
            ?.let { Log.e("ServerRepository", "Failed to getOrCompute photos ${it.string()}") }

        DownloadResponse.UNSUCCESSFUL
    }

    suspend fun trashNetworkPhoto(photoId: Long, trash: Boolean): Boolean {
        val service = photosService.get()
        val response = if (trash) service.trashPhoto(photoId) else service.unTrashPhoto(photoId)
        val successful = response.isSuccessful

        if (successful) {
            Log.d("PhotosListRepository", "Trashed photo ($trash): $photoId")
            networkPhotosDao.insert(response.body()!!)
        } else {
            Log.e("PhotosListRepository", "Failed to trash photo ($trash): $photoId, code=${response.code()}, error=${response.errorBody()?.string()}")
        }

        return successful
    }

    suspend fun deleteNetworkPhoto(photoId: Long): Boolean {
        val response = photosService.get().deletePhoto(photoId)
        val successful = response.isSuccessful

        if (successful) {
            Log.d("PhotosListRepository", "Deleting photo $photoId")
            networkPhotosDao.delete(photoId)
        }

        return successful
    }

    suspend fun getExifData(photo: NetworkPhoto): ExifData? {
        return photosService.get().getPhotoExif(photo.id).body()?.run {
            ExifData(this)
        }
    }

    suspend fun movePhotos(
        photos: List<NetworkPhoto>,
        makePublic: Boolean,
        newFolderName: String?,
    ): Boolean {
        val response = photosService.get().movePhotos(
            photoId = photos.map { it.id },
            makePublic = makePublic,
            newFolderName = newFolderName
        )

        val changedPhotos = response.body()
        Log.d("Moving Photos", response.errorBody()?.string().orEmpty())
        if (!response.isSuccessful || changedPhotos == null)
            return false

        networkPhotosDao.insert(changedPhotos)
        return true
    }

    suspend fun updateFavorite(photo: NetworkPhoto, add: Boolean) {
        val response = if (add) {
            photosService.get().addFavorite(photo.id)
        } else {
            photosService.get().removeFavorite(photo.id)
        }

        if (response.isSuccessful) {
            if (add)
                favoritePhotosDao.addFavorite(photo.id)
            else
                favoritePhotosDao.removeFavorite(photo.id)
        } else {
            Log.e(
                "ServerRepository",
                "Failed to add/remove favorite: " + response.errorBody()?.string()
            )
        }
    }

    suspend fun renameNetworkFolder(
        folder: NetworkFolder,
        makePublic: Boolean,
        newName: String?
    ): Boolean {
        val response = photosService.get().renameFolder(
            isPublic = folder.isPublic,
            folderName = folder.name,
            targetMakePublic = makePublic,
            targetFolderName = newName
        )
        Log.d("ServerRepository", "Renamed folder ${folder.name}")

        val changedPhotos = response.body()
        if (!response.isSuccessful || changedPhotos == null) {
            Log.e("ServerRepository", response.errorBody()?.string().orEmpty())
            return false
        }

        networkPhotosDao.insert(changedPhotos)
        Log.d("ServerRepository", "Updated moved folder photos (${changedPhotos.size})")
        return true
    }

    suspend fun getDuplicates(): List<List<NetworkPhoto>>? {
        val response = photosService.get().getDuplicates()
        response.body()?.let { list ->
            return list.map { ids ->
                ids.mapNotNull { id -> networkPhotosDao.findById(id) }
            }
        }

        return null
    }
}
