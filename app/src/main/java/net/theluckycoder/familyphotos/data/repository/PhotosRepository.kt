package net.theluckycoder.familyphotos.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.theluckycoder.familyphotos.data.local.db.FavoritePhotosDao
import net.theluckycoder.familyphotos.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Needed for WorkManager
class PhotosRepository @Inject constructor(
    private val localPhotosDao: LocalPhotosDao,
    private val networkPhotosDao: NetworkPhotosDao,
    private val favoritePhotosDao: FavoritePhotosDao,
) {
    fun getLocalPhoto(photoId: Long) = localPhotosDao.findById(photoId)

    fun getLocalPhotoFlow(photoId: Long) = localPhotosDao.findByIdFlow(photoId)

    fun getNetworkPhoto(photoId: Long) = networkPhotosDao.findById(photoId)

    fun getNetworkPhotoFlow(photoId: Long) = networkPhotosDao.findByIdFlow(photoId)

    suspend fun getLocalPhotoFromNetwork(networkPhotoId: Long) =
        localPhotosDao.findByNetworkId(networkPhotoId)

    fun getMemories(photoType: PhotoType): Flow<Map<Int, List<NetworkPhoto>>> =
        networkPhotosDao.getPhotosGroupedByYearsAgo(photoType).map { photosWithOffset ->
            photosWithOffset.groupBy { it.yearOffset }
                .mapValues { entry -> entry.value.map { it.photo } }
        }

    suspend fun removeNetworkReference(photo: NetworkPhoto) {
        getLocalPhotoFromNetwork(photo.id)?.let { localPhoto ->
            localPhotosDao.insertOrReplace(localPhoto.copy(networkPhotoId = 0L))
        }
    }

    fun getAllPhotosPaged(photoType: PhotoType) = networkPhotosDao.getPhotosPaged(photoType)

    fun getFavoritePhotosPaged() = favoritePhotosDao.getFavoritePhotosPaged()

    fun isNetworkPhotoFavorite(photoId: Long) = favoritePhotosDao.isFavorite(photoId)

    fun getTrashedPhotos() = networkPhotosDao.getTrashedPhotos()

    fun getMonthSummaries(photoType: PhotoType) = networkPhotosDao.getMonthSummaries(photoType)

    fun getPhotoStatistics() = networkPhotosDao.getPhotoStatistics()
}