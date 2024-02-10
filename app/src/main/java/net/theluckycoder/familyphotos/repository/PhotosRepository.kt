package net.theluckycoder.familyphotos.repository

import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.db.dao.LocalPhotosDao
import net.theluckycoder.familyphotos.db.dao.NetworkPhotosDao
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.PUBLIC_USER_ID
import net.theluckycoder.familyphotos.model.Photo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Needed for WorkManager
class PhotosRepository @Inject constructor(
    private val localPhotosDao: LocalPhotosDao,
    private val networkPhotosDao: NetworkPhotosDao,
) {

    fun getLocalPhoto(photoId: Long) = localPhotosDao.findById(photoId)

    fun getNetworkPhoto(photoId: Long) = networkPhotosDao.findById(photoId)

    suspend fun getLocalPhotoFromNetwork(networkPhotoId: Long) =
        localPhotosDao.findByNetworkId(networkPhotoId)

    fun getMemories(timestamp: Long, userName: String? = null) =
        networkPhotosDao.getPhotosInThisWeek(userName ?: PUBLIC_USER_ID, timestamp)

    fun getPhotosInProximity(photo: Photo): Flow<List<NetworkPhoto>> {
        return when (photo) {
            is NetworkPhoto -> networkPhotosDao.getPhotosInThisMonth(
                photo.userId,
                photo.timeCreated
            )

            is LocalPhoto -> TODO()
        }
    }

    suspend fun removeNetworkReference(photo: NetworkPhoto) {
        getLocalPhotoFromNetwork(photo.id)?.let { localPhoto ->
            localPhotosDao.update(localPhoto.copy(networkPhotoId = 0L))
        }
    }

    suspend fun removeMissingNetworkReferences() {
        val networkPhotos = networkPhotosDao.getAll().mapTo(HashSet()) { it.id }
        if (networkPhotos.isEmpty()) {
            return
        }

        val localPhotos = localPhotosDao.getAll().asSequence()
            .filter { it.isSavedToCloud }
            .filterNot { networkPhotos.contains(it.networkPhotoId) }
            .map { it.copy(networkPhotoId = 0) }
            .toList()

        if (localPhotos.isNotEmpty()) {
            localPhotosDao.insertOrReplace(localPhotos)
        }
    }

    fun getPersonalPhotosPaged(userName: String) = networkPhotosDao.getPhotosPaged(userName)

    fun getPublicPhotosPaged() = networkPhotosDao.getPhotosPaged(PUBLIC_USER_ID)

    fun getFavoritePhotos() = networkPhotosDao.getFavoritePhotos()
}