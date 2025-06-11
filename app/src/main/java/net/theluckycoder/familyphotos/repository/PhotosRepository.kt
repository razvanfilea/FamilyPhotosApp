package net.theluckycoder.familyphotos.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.theluckycoder.familyphotos.db.dao.LocalPhotosDao
import net.theluckycoder.familyphotos.db.dao.NetworkPhotosDao
import net.theluckycoder.familyphotos.model.NetworkPhoto
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

    fun getMemories(userName: String?): Flow<Map<Int, List<NetworkPhoto>>> =
        networkPhotosDao.getPhotosGroupedByYearsAgo(userName).map { photosWithOffset ->
            photosWithOffset.groupBy { it.yearOffset }
                .mapValues { entry -> entry.value.map { it.photo } }
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

    fun getAllPhotosPaged() = networkPhotosDao.getPhotosPaged()

    fun getFavoritePhotosPaged() = networkPhotosDao.getFavoritePhotosPaged()
}