package net.theluckycoder.familyphotos.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.local.db.FavoritePhotosDao
import net.theluckycoder.familyphotos.core.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.core.data.model.MemoriesBuilder
import net.theluckycoder.familyphotos.core.data.model.Memory
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class PhotosRepository @Inject internal constructor(
    private val localPhotosDao: LocalPhotosDao,
    private val networkPhotosDao: NetworkPhotosDao,
    private val favoritePhotosDao: FavoritePhotosDao,
    private val userDataStore: UserDataStore,
) {
    private val currentUserId: String get() = userDataStore.user.value?.userId ?: ""

    fun getLocalPhoto(photoId: Long) = localPhotosDao.findById(photoId)

    fun getLocalPhotoFlow(photoId: Long) = localPhotosDao.findByIdFlow(photoId)

    suspend fun getNetworkPhoto(photoId: Long) = networkPhotosDao.findById(photoId)

    fun getNetworkPhotoFlow(photoId: Long) = networkPhotosDao.findByIdFlow(photoId)

    suspend fun getLocalPhotoFromNetwork(networkPhotoId: Long) =
        localPhotosDao.findByNetworkId(networkPhotoId)

    /** Re-computed at midnight: memories are relative to today. */
    fun getMemories(photoType: PhotoType): Flow<List<Memory>> =
        todayFlow().flatMapLatest { today -> memoriesFlow(photoType, currentUserId, today) }

    suspend fun getMemoriesNow(photoType: PhotoType): List<Memory> {
        val userId = userDataStore.getUser()?.userId ?: return emptyList()
        return memoriesFlow(photoType, userId, LocalDate.now()).first()
    }

    private fun memoriesFlow(photoType: PhotoType, userId: String, today: LocalDate): Flow<List<Memory>> {
        val zone = ZoneId.systemDefault()
        // A few days of slack on both ends: MemoriesBuilder does the exact matching
        val minTime = today.minusYears(MemoriesBuilder.MAX_YEARS_AGO.toLong()).minusDays(7)
            .atStartOfDay(zone).toEpochSecond()
        val maxTime = today.minusYears(MemoriesBuilder.MIN_YEARS_AGO.toLong()).plusDays(7)
            .atStartOfDay(zone).toEpochSecond()

        // The same "today" for the query and the matching, even if the query re-runs after midnight
        return networkPhotosDao
            .getMemoryCandidates(photoType, userId, today.dayOfYear, minTime, maxTime)
            .map { candidates -> MemoriesBuilder.build(candidates, today, zone) }
    }

    private fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            emit(today)
            val untilMidnight = Duration.between(ZonedDateTime.now(zone), today.plusDays(1).atStartOfDay(zone))
            delay(untilMidnight.toMillis().coerceAtLeast(1_000L))
        }
    }.distinctUntilChanged()

    suspend fun removeNetworkReference(photo: NetworkPhoto) {
        getLocalPhotoFromNetwork(photo.id)?.let { localPhoto ->
            localPhotosDao.insertOrReplace(localPhoto.copy(networkPhotoId = 0L))
        }
    }

    fun getAllPhotosPaged(photoType: PhotoType) =
        networkPhotosDao.getPhotosPaged(photoType, currentUserId)

    fun getFavoritePhotosPaged() = favoritePhotosDao.getFavoritePhotosPaged()

    fun getFavoriteMonthSummaries() = favoritePhotosDao.getMonthSummaries()

    fun isNetworkPhotoFavorite(photoId: Long) = favoritePhotosDao.isFavorite(photoId)

    fun getTrashedPhotos() = networkPhotosDao.getTrashedPhotos()

    fun getMonthSummaries(photoType: PhotoType) =
        networkPhotosDao.getMonthSummaries(photoType, currentUserId)

    fun getPhotoStatistics() = networkPhotosDao.getPhotoStatistics(currentUserId)

    fun getLargePhotos(minSizeBytes: Long = 52_428_800L) =
        networkPhotosDao.getLargePhotos(minSizeBytes)
}