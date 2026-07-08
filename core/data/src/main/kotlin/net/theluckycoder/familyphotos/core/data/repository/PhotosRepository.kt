package net.theluckycoder.familyphotos.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.local.db.FavoritePhotosDao
import net.theluckycoder.familyphotos.core.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Needed for WorkManager
class PhotosRepository @Inject internal constructor(
    private val localPhotosDao: LocalPhotosDao,
    private val networkPhotosDao: NetworkPhotosDao,
    private val favoritePhotosDao: FavoritePhotosDao,
    private val userDataStore: UserDataStore,
) {
    private val currentUserId: String get() = userDataStore.userId.value ?: ""

    fun getLocalPhoto(photoId: Long) = localPhotosDao.findById(photoId)

    fun getLocalPhotoFlow(photoId: Long) = localPhotosDao.findByIdFlow(photoId)

    suspend fun getNetworkPhoto(photoId: Long) = networkPhotosDao.findById(photoId)

    fun getNetworkPhotoFlow(photoId: Long) = networkPhotosDao.findByIdFlow(photoId)

    suspend fun getLocalPhotoFromNetwork(networkPhotoId: Long) =
        localPhotosDao.findByNetworkId(networkPhotoId)

    fun getMemories(photoType: PhotoType): Flow<Map<Int, List<NetworkPhoto>>> =
        networkPhotosDao.getPhotosGroupedByYearsAgo(photoType, currentUserId).map { photosWithOffset ->
            photosWithOffset.groupBy { it.yearOffset }
                .mapValues { entry -> entry.value.map { it.photo } }
        }

    suspend fun removeNetworkReference(photo: NetworkPhoto) {
        getLocalPhotoFromNetwork(photo.id)?.let { localPhoto ->
            localPhotosDao.insertOrReplace(localPhoto.copy(networkPhotoId = 0L))
        }
    }

    fun getAllPhotosPaged(photoType: PhotoType) = networkPhotosDao.getPhotosPaged(photoType, currentUserId)

    fun getFavoritePhotosPaged() = favoritePhotosDao.getFavoritePhotosPaged()

    fun isNetworkPhotoFavorite(photoId: Long) = favoritePhotosDao.isFavorite(photoId)

    fun getTrashedPhotos() = networkPhotosDao.getTrashedPhotos()

    fun getMonthSummaries(photoType: PhotoType) = networkPhotosDao.getMonthSummaries(photoType, currentUserId)

    fun getPhotoStatistics() = networkPhotosDao.getPhotoStatistics(currentUserId)

    fun getLargePhotos(minSizeBytes: Long = 52_428_800L) = networkPhotosDao.getLargePhotos(minSizeBytes)
}