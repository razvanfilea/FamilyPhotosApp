package net.theluckycoder.familyphotos.core.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.core.data.model.network.PhotoEventLog
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.model.db.MonthSummary
import net.theluckycoder.familyphotos.core.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.db.PhotoStatistics
import net.theluckycoder.familyphotos.core.data.model.db.NetworkPhotoWithYearOffset

@Dao
internal interface NetworkPhotosDao {

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    fun findById(photoId: Long): NetworkPhoto?

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    fun findByIdFlow(photoId: Long): Flow<NetworkPhoto?>

    @Query(
        """SELECT * FROM network_photo
            WHERE CASE
                WHEN :photoType = 1 THEN (userId = :currentUserId)
                WHEN :photoType = 2 THEN (userId IS NULL)
                ELSE 1
            END
            AND trashedOn IS NULL
            ORDER BY network_photo.timeCreated DESC"""
    )
    fun getPhotosPaged(photoType: PhotoType, currentUserId: String): PagingSource<Int, NetworkPhoto>

    @Query(
        """SELECT * FROM network_photo
        WHERE network_photo.folderId = :folderId
        AND CASE
            WHEN :photoType = 1 THEN (userId = :currentUserId)
            WHEN :photoType = 2 THEN (userId IS NULL)
            ELSE 1
        END
        AND trashedOn IS NULL
        ORDER BY network_photo.timeCreated DESC"""
    )
    fun getFolderPhotos(folderId: Long, photoType: PhotoType, currentUserId: String): PagingSource<Int, NetworkPhoto>


    @Query(
        """SELECT *,
              CAST(strftime('%Y', 'now') AS INTEGER) - CAST(strftime('%Y', datetime(network_photo.timeCreated, 'unixepoch')) AS INTEGER) AS yearOffset
        FROM network_photo
        WHERE CASE
            WHEN :photoType = 1 THEN (userId = :currentUserId)
            WHEN :photoType = 2 THEN (userId IS NULL)
            ELSE 1
        END
        AND yearOffset BETWEEN :minYearsAgo AND :maxYearsAgo
        AND MIN(
            ABS(CAST(strftime('%j', 'now') AS INTEGER) - CAST(strftime('%j', datetime(network_photo.timeCreated, 'unixepoch')) AS INTEGER)),
            366 - ABS(CAST(strftime('%j', 'now') AS INTEGER) - CAST(strftime('%j', datetime(network_photo.timeCreated, 'unixepoch')) AS INTEGER))
        ) <= 3
        AND trashedOn is null
        ORDER BY yearOffset ASC, network_photo.timeCreated DESC"""
    )
    fun getPhotosGroupedByYearsAgo(
        photoType: PhotoType,
        currentUserId: String,
        minYearsAgo: Int = 1,
        maxYearsAgo: Int = 10
    ): Flow<List<NetworkPhotoWithYearOffset>>

    @Query(
        """
        SELECT MAX(timeCreated) as timeCreated,
               id as coverPhotoId,
               COUNT(*) as photoCount
        FROM network_photo
        WHERE trashedOn IS NULL
        AND CASE
            WHEN :photoType = 1 THEN (userId = :currentUserId)
            WHEN :photoType = 2 THEN (userId IS NULL)
            ELSE 1
        END
        GROUP BY strftime('%Y-%m', datetime(timeCreated, 'unixepoch', 'localtime'))
        ORDER BY timeCreated DESC
        """
    )
    fun getMonthSummaries(photoType: PhotoType, currentUserId: String): Flow<List<MonthSummary>>

    @Query(
        """
        SELECT MAX(timeCreated) as timeCreated,
               id as coverPhotoId,
               COUNT(*) as photoCount
        FROM network_photo
        WHERE folderId = :folderId
        AND trashedOn IS NULL
        AND CASE
            WHEN :photoType = 1 THEN (userId = :currentUserId)
            WHEN :photoType = 2 THEN (userId IS NULL)
            ELSE 1
        END
        GROUP BY strftime('%Y-%m', datetime(timeCreated, 'unixepoch', 'localtime'))
        ORDER BY timeCreated DESC
        """
    )
    fun getMonthSummariesForFolder(folderId: Long, photoType: PhotoType, currentUserId: String): Flow<List<MonthSummary>>

    @Query("SELECT * FROM network_photo WHERE trashedOn IS NOT NULL ORDER BY trashedOn DESC")
    fun getTrashedPhotos(): Flow<List<NetworkPhoto>>

    @Query(
        """
        SELECT * FROM network_photo
        WHERE trashedOn IS NULL AND fileSize > :minSizeBytes
        ORDER BY fileSize DESC
        """
    )
    fun getLargePhotos(minSizeBytes: Long): Flow<List<NetworkPhoto>>

    @Query(
        """
        WITH photos AS (
            SELECT userId, fileSize,
                CASE WHEN LOWER(name) LIKE '%.mp4' OR LOWER(name) LIKE '%.mov'
                     OR LOWER(name) LIKE '%.mkv' OR LOWER(name) LIKE '%.webm'
                     OR LOWER(name) LIKE '%.avi' OR LOWER(name) LIKE '%.3gp'
                THEN 1 ELSE 0 END as isVideo
            FROM network_photo
            WHERE trashedOn IS NULL
            AND (userId = :currentUserId OR userId IS NULL)
        )
        SELECT
            COUNT(CASE WHEN userId IS NULL THEN 1 END) as familyCount,
            COUNT(CASE WHEN userId = :currentUserId THEN 1 END) as personalCount,
            COALESCE(SUM(CASE WHEN userId IS NULL THEN fileSize ELSE 0 END), 0) as familySize,
            COALESCE(SUM(CASE WHEN userId = :currentUserId THEN fileSize ELSE 0 END), 0) as personalSize,
            COUNT(CASE WHEN isVideo = 0 THEN 1 END) as imageCount,
            COUNT(CASE WHEN isVideo = 1 THEN 1 END) as videoCount,
            COALESCE(SUM(CASE WHEN isVideo = 0 THEN fileSize ELSE 0 END), 0) as imageSize,
            COALESCE(SUM(CASE WHEN isVideo = 1 THEN fileSize ELSE 0 END), 0) as videoSize
        FROM photos
        """
    )
    fun getPhotoStatistics(currentUserId: String): Flow<PhotoStatistics>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: NetworkPhoto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: Collection<NetworkPhoto>)

    @Query("DELETE FROM network_photo WHERE id = :photoId")
    suspend fun delete(photoId: Long)

    @Query("SELECT eventLogId FROM server_state LIMIT 1")
    suspend fun getEventLogId(): Long

    @Query("UPDATE server_state SET eventLogId = :eventLogId WHERE id = 0")
    suspend fun updateEventLogId(eventLogId: Long)

    @Transaction
    suspend fun updatePartialSync(events: Collection<PhotoEventLog>, eventLogId: Long) {
        for (event in events) {
            val photo = event.decodePhoto()
            if (photo != null) {
                insert(photo)
            } else {
                delete(event.photoId)
            }
        }

        updateEventLogId(eventLogId)
    }

    @Transaction
    suspend fun updateFullSync(
        list: Collection<NetworkPhoto>,
        eventLogId: Long,
        userId: String,
    ) {
        deletePersonalAndPublicPhotos(userId)
        insert(list)
        updateEventLogId(eventLogId)
    }

    @Query("DELETE FROM network_photo WHERE userId = :userId OR userId IS NULL")
    suspend fun deletePersonalAndPublicPhotos(userId: String)

    @Query("DELETE FROM network_photo WHERE folderId = :folderId")
    suspend fun deletePhotosByFolderId(folderId: Long)

    @Transaction
    suspend fun replaceSharedFolderPhotos(folderId: Long, photos: List<NetworkPhoto>) {
        deletePhotosByFolderId(folderId)
        insert(photos)
    }

    @Transaction
    suspend fun applySharedFolderEvents(events: List<PhotoEventLog>) {
        for (event in events) {
            val photo = event.decodePhoto()
            if (photo != null) {
                insert(photo)
            } else {
                delete(event.photoId)
            }
        }
    }
}
