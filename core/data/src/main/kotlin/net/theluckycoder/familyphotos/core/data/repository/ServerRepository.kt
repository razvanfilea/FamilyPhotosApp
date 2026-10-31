package net.theluckycoder.familyphotos.core.data.repository

import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
import net.theluckycoder.familyphotos.core.data.local.db.PhotosDatabase
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.core.data.local.datastore.SharedActivityStore
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.local.db.FavoritePhotosDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkFoldersDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.core.data.model.ExifData
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.UploadChoice
import net.theluckycoder.familyphotos.core.data.model.db.NetworkFolderEntity
import net.theluckycoder.familyphotos.core.data.model.network.CreateFolderRequest
import net.theluckycoder.familyphotos.core.data.model.network.PhotoEventLog
import net.theluckycoder.familyphotos.core.data.model.network.toEntity
import net.theluckycoder.familyphotos.core.data.model.sharing.FolderSyncDelta
import net.theluckycoder.familyphotos.core.data.model.sharing.PhotoEvent
import net.theluckycoder.familyphotos.core.data.model.sharing.SharedFolderChange
import net.theluckycoder.familyphotos.core.data.model.sharing.SharedFolderChangeDetector
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
    private val settingsDataStore: SettingsDataStore,
    private val sharedActivityStore: SharedActivityStore,
    private val database: PhotosDatabase,
) {

    private companion object {
        // Syncs can overlap (app refresh, backup worker, notification worker); each delta must be read once
        val syncMutex = Mutex()
    }

    enum class DownloadResponse {
        SUCCESSFUL,
        UNSUCCESSFUL,
        NOT_LOGGED_IN,
        FULL_DOWNLOAD_NEEDED
    }

    suspend fun downloadPartialPhotos(): DownloadResponse {
        val currentUserId = userDataStore.getUser()?.userId ?: return DownloadResponse.NOT_LOGGED_IN
        return syncMutex.withLock { downloadPartialPhotosLocked(currentUserId) }
    }

    private suspend fun downloadPartialPhotosLocked(currentUserId: String): DownloadResponse {
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
                // Has to be read before the events are applied to the DB
                val changes = if (lastSyncedEventLogId != 0L) {
                    detectOwnFolderChanges(currentUserId, partialPhotos.events)
                } else {
                    emptyList()
                }
                networkPhotosDao.updatePartialSync(partialPhotos.events, partialPhotos.eventLogId)
                if (changes.isNotEmpty()) sharedActivityStore.recordSync(currentUserId, changes)
            }

            Log.i(
                "ServerRepository",
                "Downloaded partial photos. EventLogId: ${partialPhotos.eventLogId}"
            )

            return DownloadResponse.SUCCESSFUL
        }
    }

    private suspend fun detectOwnFolderChanges(
        currentUserId: String,
        events: Collection<PhotoEventLog>,
    ): List<SharedFolderChange> {
        if (!settingsDataStore.notifySharedFolders.first()) return emptyList()
        // Before the first folder sync of this user every photo would look new
        if (sharedActivityStore.isFirstSync(currentUserId)) return emptyList()

        val ownFolderIds = networkFoldersDao.getOwnedFolderIds(currentUserId).toSet()
        if (ownFolderIds.isEmpty()) return emptyList()

        val photoEvents = events.map { event ->
            val photo = event.decodePhoto()
            PhotoEvent(event.photoId, present = photo != null && photo.trashedOn == null, folderId = photo?.folderId)
        }
        val candidates = photoEvents.filter { it.present && it.folderId in ownFolderIds }
            .map { it.photoId }.distinct()
        if (candidates.isEmpty()) return emptyList()

        // Kept under SQLite's bound-parameter limit
        val alreadyKnown = candidates.chunked(500)
            .flatMap { networkPhotosDao.getExistingPhotoIds(it) }
            .toSet()

        return SharedFolderChangeDetector.detectInOwnFolders(currentUserId, ownFolderIds, alreadyKnown, photoEvents)
    }

    suspend fun downloadUserPhotos(): DownloadResponse {
        val userId = userDataStore.getUser()?.userId ?: return DownloadResponse.NOT_LOGGED_IN
        return syncMutex.withLock { downloadUserPhotosLocked(userId) }
    }

    private suspend fun downloadUserPhotosLocked(userId: String): DownloadResponse = coroutineScope {

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

    suspend fun syncFolders(): Boolean = syncFoldersWithChanges() != null

    /**
     * Syncs the shared folders and reports what changed for the user (for notifications).
     * The changes are also recorded in [SharedActivityStore], because this sync is run by several
     * callers and only the first one to run sees the server-side delta.
     *
     * @return null if the sync failed
     */
    suspend fun syncFoldersWithChanges(): List<SharedFolderChange>? {
        // `user` is still null right after a cold start, which is when background workers run
        val currentUserId = userDataStore.getUser()?.userId ?: return null

        return syncMutex.withLock { syncFoldersLocked(currentUserId) }
    }

    private suspend fun syncFoldersLocked(currentUserId: String): List<SharedFolderChange>? {
        val localCursors = networkFoldersDao.getSharedFolderCursors(currentUserId)
        val requestBody = localCursors.associate { it.id to it.latestEventId }
        val isFirstSync = sharedActivityStore.isFirstSync(currentUserId)

        val response = syncService.get().syncFolders(requestBody)
        if (!response.isSuccessful) {
            Log.e("ServerRepository", "Folder sync failed: ${response.code()}")
            return null
        }

        val responseFolders = response.body()!!

        // Has to be read before the events are applied to the DB
        val deltas = responseFolders.map { folder ->
            FolderSyncDelta(
                id = folder.id,
                ownerId = folder.ownerId,
                isFullList = folder.photos != null,
                events = folder.events.orEmpty().map { event ->
                    val photo = event.decodePhoto()
                    PhotoEvent(event.photoId, present = photo != null && photo.trashedOn == null)
                },
            )
        }
        val photoIdsAlreadyInFolder = deltas
            .filter { it.id in requestBody && it.events.any { e -> e.present } }
            .associate { it.id to networkPhotosDao.getPhotoIdsInFolder(it.id).toSet() }
        val changes = if (settingsDataStore.notifySharedFolders.first()) {
            SharedFolderChangeDetector.detect(
                currentUserId = currentUserId,
                isFirstSync = isFirstSync,
                knownFolderIds = requestBody.keys,
                photoIdsAlreadyInFolder = photoIdsAlreadyInFolder,
                folders = deltas,
            )
        } else {
            emptyList()
        }

        // Events and cursors change together: if this fails midway the next sync must see the same delta
        database.withTransaction {
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
        }

        sharedActivityStore.recordSync(currentUserId, changes)

        return changes
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
