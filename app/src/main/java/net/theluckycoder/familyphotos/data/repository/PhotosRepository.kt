package net.theluckycoder.familyphotos.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.theluckycoder.familyphotos.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.data.model.NetworkPhoto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Needed for WorkManager
class PhotosRepository @Inject constructor(
    private val localPhotosDao: LocalPhotosDao,
    private val networkPhotosDao: NetworkPhotosDao,
) {
    fun getLocalPhoto(photoId: Long) = localPhotosDao.findById(photoId)

    fun getLocalPhotoFlow(photoId: Long) = localPhotosDao.findByIdFlow(photoId)

    fun getNetworkPhoto(photoId: Long) = networkPhotosDao.findById(photoId)

    fun getNetworkPhotoFlow(photoId: Long) = networkPhotosDao.findByIdFlow(photoId)

    suspend fun getLocalPhotoFromNetwork(networkPhotoId: Long) =
        localPhotosDao.findByNetworkId(networkPhotoId)

    fun getMemories(userName: String?): Flow<Map<Int, List<NetworkPhoto>>> =
        networkPhotosDao.getPhotosGroupedByYearsAgo(userName).map { photosWithOffset ->
            photosWithOffset.groupBy { it.yearOffset }
                .mapValues { entry -> entry.value.map { it.photo } }
        }

    suspend fun removeNetworkReference(photo: NetworkPhoto) {
        getLocalPhotoFromNetwork(photo.id)?.let { localPhoto ->
            localPhotosDao.insertOrReplace(localPhoto.copy(networkPhotoId = 0L))
        }
    }

    fun getAllPhotosPaged() = networkPhotosDao.getPhotosPaged()

    fun getFavoritePhotosPaged() = networkPhotosDao.getFavoritePhotosPaged()
}