package net.theluckycoder.familyphotos.core.data.repository

import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.local.db.FavoritePhotosDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkFoldersDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.core.data.model.db.NetworkFolderEntity
import net.theluckycoder.familyphotos.core.data.model.network.ExifData
import net.theluckycoder.familyphotos.core.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.remote.PhotosService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles network API operations for photos.
 * MediaStore operations are in [MediaStoreRepository].
 * Upload operations are in [PhotoUploadRepository].
 */
@Singleton
class ServerRepository @Inject internal constructor(
    private val photosService: Lazy<PhotosService>,
    private val networkPhotosDao: NetworkPhotosDao,
    private val networkFoldersDao: NetworkFoldersDao,
    private val favoritePhotosDao: FavoritePhotosDao,
    private val userDataStore: UserDataStore,
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
                networkPhotosDao.updatePartialSync(partialPhotos.events, partialPhotos.eventLogId)
            }

            Log.i(
                "ServerRepository",
                "Downloaded partial photos. EventLogId: ${partialPhotos.eventLogId}"
            )

            return DownloadResponse.SUCCESSFUL
        }
    }

    suspend fun downloadUserPhotos(): DownloadResponse = coroutineScope {
        val userId = userDataStore.userId.value
            ?: return@coroutineScope DownloadResponse.NOT_LOGGED_IN

        val service = photosService.get()
        val photosAsync = async { service.getFullPhotosList() }

        val favoritePhotosResponse = service.getFavorites()
        favoritePhotosResponse.errorBody()
            ?.let { Log.e("ServerRepository", "Failed to getOrCompute favorites ${it.string()}") }

        val photosResponse = photosAsync.await()

        if (photosResponse.isSuccessful) {
            val fullPhotos = photosResponse.body()!!

            if (fullPhotos.photos.isNotEmpty()) {
                networkPhotosDao.updateFullSync(fullPhotos.photos, fullPhotos.eventLogId, userId)
                Log.i(
                    "ServerRepository",
                    "Downloaded all user photos. EventLogId: ${fullPhotos.eventLogId}"
                )

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

    suspend fun syncFolders(): Boolean {
        val currentUserId = userDataStore.userId.value ?: return false

        val localCursors = networkFoldersDao.getSharedFolderCursors(currentUserId)
        val requestBody = localCursors.associate { it.id to it.latestEventId }

        val response = photosService.get().syncFolders(requestBody)
        if (!response.isSuccessful) {
            Log.e("ServerRepository", "Folder sync failed: ${response.code()}")
            return false
        }

        val responseFolders = response.body()!!

        val responseFolderIds = responseFolders.map { it.id }.toSet()
        val localSharedFolderIds = localCursors.map { it.id }.toSet()
        for (folderId in (localSharedFolderIds - responseFolderIds)) {
            networkPhotosDao.deletePhotosByFolderId(folderId)
        }

        for (folder in responseFolders) {
            when {
                folder.photos != null -> networkPhotosDao.replaceSharedFolderPhotos(folder.id, folder.photos)
                folder.events != null -> networkPhotosDao.applySharedFolderEvents(folder.events)
            }
        }

        val existingCursors = localCursors.associate { it.id to it.latestEventId }
        val folderEntities = responseFolders.map { folder ->
            NetworkFolderEntity(
                id = folder.id,
                ownerId = folder.ownerId,
                name = folder.name,
                latestEventId = folder.latestEventId ?: existingCursors[folder.id] ?: 0L,
                createdAt = 0L,
            )
        }
        networkFoldersDao.replaceAll(folderEntities)

        return true
    }

    suspend fun trashNetworkPhoto(photoIds: LongArray, trash: Boolean): Boolean {
        val service = photosService.get()
        val response = if (trash) service.trashPhotos(photoIds.toList()) else service.restorePhotos(photoIds.toList())
        val successful = response.isSuccessful

        if (successful) {
            Log.d("PhotosListRepository", "Trashed photo ($trash): $photoIds")
            networkPhotosDao.insert(response.body()!!)
        } else {
            Log.e("PhotosListRepository", "Failed to trash photo ($trash): $photoIds, code=${response.code()}, error=${response.errorBody()?.string()}")
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
        syncFolders()
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
        folderId: Long,
        makePublic: Boolean,
        newName: String?
    ): Boolean {
        val response = photosService.get().renameFolder(
            sourceFolderId = folderId,
            targetMakePublic = makePublic,
            targetFolderName = newName
        )
        Log.d("ServerRepository", "Renamed folder $folderId")

        val changedPhotos = response.body()
        if (!response.isSuccessful || changedPhotos == null) {
            Log.e("ServerRepository", response.errorBody()?.string().orEmpty())
            return false
        }

        networkPhotosDao.insert(changedPhotos)
        syncFolders()
        Log.d("ServerRepository", "Updated moved folder photos (${changedPhotos.size})")
        return true
    }

    suspend fun getFolderName(folderId: Long): String? =
        networkFoldersDao.getFolderName(folderId)

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
