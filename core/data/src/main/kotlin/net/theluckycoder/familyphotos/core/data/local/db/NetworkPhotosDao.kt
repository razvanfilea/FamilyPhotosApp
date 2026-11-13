package net.theluckycoder.familyphotos.core.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.NetworkPhotoThumbnail
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.model.db.MonthSummary
import net.theluckycoder.familyphotos.core.data.model.db.MemoryCandidate
import net.theluckycoder.familyphotos.core.data.model.db.PhotoStatistics
import net.theluckycoder.familyphotos.core.data.model.db.YearSummary
import net.theluckycoder.familyphotos.core.data.model.network.PhotoEventLog

/** Shared by the folder grid and the folder slideshow, which must show the same photos in the same order. */
private const val FOLDER_PHOTOS_QUERY = """SELECT id, timeCreated, thumbHash, name FROM network_photo
    WHERE network_photo.folderId = :folderId
    AND trashedOn IS NULL
    ORDER BY network_photo.timeCreated DESC"""

/**
 * Shared by the search grid and its month headers. The text matches the file name or the folder's
 * name; a null year/month matches any. Searches all the photos the user can see.
 */
private const val SEARCH_WHERE = """p.trashedOn IS NULL
    AND (:text = ''
        OR p.name LIKE '%' || :text || '%' ESCAPE '\'
        OR p.folderId IN (SELECT id FROM network_folder WHERE name LIKE '%' || :text || '%' ESCAPE '\'))
    AND (:year IS NULL OR CAST(strftime('%Y', p.timeCreated, 'unixepoch', 'localtime') AS INTEGER) = :year)
    AND (:month IS NULL OR CAST(strftime('%m', p.timeCreated, 'unixepoch', 'localtime') AS INTEGER) = :month)
    AND (:videosOnly = 0 OR LOWER(p.name) LIKE '%.mp4' OR LOWER(p.name) LIKE '%.mov'
        OR LOWER(p.name) LIKE '%.mkv' OR LOWER(p.name) LIKE '%.webm'
        OR LOWER(p.name) LIKE '%.avi' OR LOWER(p.name) LIKE '%.3gp')
    AND (:favoritesOnly = 0 OR EXISTS(SELECT 1 FROM favorite_network_photo f WHERE f.photoId = p.id))"""

@Dao
internal interface NetworkPhotosDao {

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    suspend fun findById(photoId: Long): NetworkPhoto?

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    fun findByIdFlow(photoId: Long): Flow<NetworkPhoto?>

    @Query(
        """SELECT id, timeCreated, thumbHash, name FROM network_photo
            WHERE CASE
                WHEN :photoType = 1 THEN (userId = :currentUserId)
                WHEN :photoType = 2 THEN (userId IS NULL)
                WHEN :photoType = 3 THEN (userId <> :currentUserId)
                ELSE 1
            END
            AND trashedOn IS NULL
            ORDER BY network_photo.timeCreated DESC"""
    )
    fun getPhotosPaged(
        photoType: PhotoType,
        currentUserId: String
    ): PagingSource<Int, NetworkPhotoThumbnail>

    @Query(FOLDER_PHOTOS_QUERY)
    fun getFolderPhotos(folderId: Long): PagingSource<Int, NetworkPhotoThumbnail>

    @Query(FOLDER_PHOTOS_QUERY)
    suspend fun getAllFolderPhotos(folderId: Long): List<NetworkPhotoThumbnail>

    @Query("SELECT id, timeCreated, thumbHash, name FROM network_photo WHERE id IN (:photoIds) AND trashedOn IS NULL")
    suspend fun getThumbnails(photoIds: List<Long>): List<NetworkPhotoThumbnail>


    /**
     * Coarse filter for memories: photos taken between [minTime] and [maxTime] whose day of the
     * year is near [todayDayOfYear]'s. `MemoriesBuilder` does the exact matching (leap years, New Year).
     */
    @Query(
        """SELECT p.id, p.timeCreated, p.thumbHash, p.name, p.fileSize,
              EXISTS(SELECT 1 FROM favorite_network_photo f WHERE f.photoId = p.id) AS isFavorite
        FROM network_photo p
        WHERE CASE
            WHEN :photoType = 1 THEN (p.userId = :currentUserId)
            WHEN :photoType = 2 THEN (p.userId IS NULL)
            WHEN :photoType = 3 THEN (p.userId <> :currentUserId)
            ELSE 1
        END
        AND p.trashedOn IS NULL
        AND p.timeCreated BETWEEN :minTime AND :maxTime
        AND MIN(
            ABS(:todayDayOfYear - CAST(strftime('%j', p.timeCreated, 'unixepoch', 'localtime') AS INTEGER)),
            366 - ABS(:todayDayOfYear - CAST(strftime('%j', p.timeCreated, 'unixepoch', 'localtime') AS INTEGER))
        ) <= 5"""
    )
    fun getMemoryCandidates(
        photoType: PhotoType,
        currentUserId: String,
        todayDayOfYear: Int,
        minTime: Long,
        maxTime: Long,
    ): Flow<List<MemoryCandidate>>

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
            WHEN :photoType = 3 THEN (userId <> :currentUserId)
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
        GROUP BY strftime('%Y-%m', datetime(timeCreated, 'unixepoch', 'localtime'))
        ORDER BY timeCreated DESC
        """
    )
    fun getMonthSummariesForFolder(folderId: Long): Flow<List<MonthSummary>>

    @Query(
        """SELECT p.id, p.timeCreated, p.thumbHash, p.name FROM network_photo p
        WHERE $SEARCH_WHERE
        ORDER BY p.timeCreated DESC"""
    )
    fun searchPhotosPaged(
        text: String,
        year: Int?,
        month: Int?,
        videosOnly: Boolean,
        favoritesOnly: Boolean,
    ): PagingSource<Int, NetworkPhotoThumbnail>

    @Query(
        """SELECT MAX(p.timeCreated) AS timeCreated, p.id AS coverPhotoId, COUNT(*) AS photoCount
        FROM network_photo p
        WHERE $SEARCH_WHERE
        GROUP BY strftime('%Y-%m', datetime(p.timeCreated, 'unixepoch', 'localtime'))
        ORDER BY timeCreated DESC"""
    )
    fun searchMonthSummaries(
        text: String,
        year: Int?,
        month: Int?,
        videosOnly: Boolean,
        favoritesOnly: Boolean,
    ): Flow<List<MonthSummary>>

    /** Each year with photos, newest first, with its latest photo (bare columns follow MAX). */
    @Query(
        """SELECT CAST(strftime('%Y', timeCreated, 'unixepoch', 'localtime') AS INTEGER) AS year,
               COUNT(*) AS photoCount, id, MAX(timeCreated) AS timeCreated, thumbHash, name
        FROM network_photo
        WHERE trashedOn IS NULL
        GROUP BY year
        ORDER BY year DESC"""
    )
    fun getYearSummaries(): Flow<List<YearSummary>>

    @Query("SELECT * FROM network_photo WHERE trashedOn IS NOT NULL ORDER BY trashedOn ASC")
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

    @Query("SELECT id FROM network_photo WHERE folderId = :folderId")
    suspend fun getPhotoIdsInFolder(folderId: Long): List<Long>

    @Query("SELECT id FROM network_photo WHERE id IN (:photoIds)")
    suspend fun getExistingPhotoIds(photoIds: List<Long>): List<Long>

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
