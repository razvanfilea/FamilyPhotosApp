package net.theluckycoder.familyphotos.core.data.repository

import android.R.attr.value
import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.local.db.FavoritePhotosDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkFoldersDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.core.data.model.db.NetworkFolderEntity
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.ExifData
import net.theluckycoder.familyphotos.core.data.model.UploadChoice
import net.theluckycoder.familyphotos.core.data.model.network.toEntity
import net.theluckycoder.familyphotos.core.data.model.network.CreateFolderRequest
import net.theluckycoder.familyphotos.core.data.remote.FolderService
import net.theluckycoder.familyphotos.core.data.remote.PhotosService
import net.theluckycoder.familyphotos.core.data.remote.SyncService
import javax.inject.Inject

/**
 * Handles network API operations for photos.
 * MediaStore operations are in [MediaStoreRepository].
 * Upload operations are in [PhotoUploadRepository].
 */
class ServerRepository @Inject internal constructor(
    private val syncService: Lazy<SyncService>,
    private val photosService: Lazy<PhotosService>,
    private val folderService: Lazy<FolderService>,
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

        val response = syncService.get().getEventLogsList(lastSyncedEventLogId)

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
        val userId = userDataStore.user.value?.userId
            ?: return@coroutineScope DownloadResponse.NOT_LOGGED_IN

        val service = syncService.get()
        val photosAsync = async { service.getFullPhotosList() }

        val favoritePhotosResponse = photosService.get().getFavorites()
        favoritePhotosResponse.errorBody()
            ?.let { Log.e("ServerRepository", "Failed to getOrCompute favorites ${it.string()}") }

        val photosResponse = photosAsync.await()

        if (photosResponse.isSuccessful) {
            val fullPhotos = photosResponse.body()!!

            if (fullPhotos.photos.isNotEmpty()) {
                networkPhotosDao.updateFullSync(
                    fullPhotos.photos.map { it.toEntity() },
                    fullPhotos.eventLogId,
                    userId
                )
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
        val currentUserId = userDataStore.user.value?.userId ?: return false

        val localCursors = networkFoldersDao.getSharedFolderCursors(currentUserId)
        val requestBody = localCursors.associate { it.id to it.latestEventId }

        val response = syncService.get().syncFolders(requestBody)
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
                folder.photos != null -> networkPhotosDao.replaceSharedFolderPhotos(
                    folder.id,
                    folder.photos.map { it.toEntity() })

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
        val response =
            if (trash) service.trashPhotos(photoIds.toList()) else service.restorePhotos(photoIds.toList())
        val successful = response.isSuccessful

        if (successful) {
            Log.d("PhotosListRepository", "Trashed photo ($trash): ${photoIds.contentToString()}")
            networkPhotosDao.insert(response.body()!!.map { it.toEntity() })
        } else {
            Log.e(
                "PhotosListRepository",
                "Failed to trash photo ($trash): ${photoIds.contentToString()}, code=${response.code()}, error=${
                    response.errorBody()?.string()
                }"
            )
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

    private suspend fun resolveUploadChoiceToFolderId(uploadChoice: UploadChoice): Result<Long?> {
        return when (uploadChoice) {
            is UploadChoice.Folder -> Result.success(uploadChoice.folderId)
            is UploadChoice.NewFolder -> {
                val response = folderService.get()
                    .createFolder(CreateFolderRequest(uploadChoice.name, uploadChoice.isPublic))
                val folderDto = response.body()
                    ?: return Result.failure(Exception("Failed to create folder"))
                networkFoldersDao.insert(
                    NetworkFolderEntity(
                        id = folderDto.id,
                        ownerId = folderDto.ownerId,
                        name = folderDto.name,
                        latestEventId = 0L,
                        createdAt = 0L,
                    )
                )
                Result.success(folderDto.id)
            }

            is UploadChoice.NoFolder -> Result.success(null)
        }
    }

    suspend fun movePhotos(photos: List<NetworkPhoto>, uploadChoice: UploadChoice): Boolean {
        val targetFolderId = resolveUploadChoiceToFolderId(uploadChoice).getOrElse { return false }

        val response = photosService.get().movePhotos(
            makePublic = uploadChoice.isPublic,
            targetFolderId = targetFolderId,
            photoId = photos.map { it.id },
        )

        val changedPhotos = response.body()
        Log.d("Moving Photos", response.errorBody()?.string().orEmpty())
        if (!response.isSuccessful || changedPhotos == null)
            return false

        networkPhotosDao.insert(changedPhotos.map { it.toEntity() })
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

    suspend fun renameFolder(folderId: Long, newName: String, isPublic: Boolean): Boolean {
        val response =
            folderService.get().updateFolder(folderId, CreateFolderRequest(newName, isPublic))
        if (!response.isSuccessful) return false
        val dto = response.body() ?: return false

        networkFoldersDao.insert(
            NetworkFolderEntity(
                id = dto.id,
                ownerId = dto.ownerId,
                name = dto.name,
                latestEventId = 0L,
                createdAt = 0L,
            )
        )
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
