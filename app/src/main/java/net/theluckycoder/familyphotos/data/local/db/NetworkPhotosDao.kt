package net.theluckycoder.familyphotos.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import net.theluckycoder.familyphotos.data.model.db.NetworkFolder
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.db.NetworkPhotoWithYearOffset
import net.theluckycoder.familyphotos.data.model.PhotoEventLog
import net.theluckycoder.familyphotos.data.model.PhotoType

@Dao
interface NetworkPhotosDao {

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    fun findById(photoId: Long): NetworkPhoto?

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    fun findByIdFlow(photoId: Long): Flow<NetworkPhoto?>

    @Query(
        """SELECT * FROM network_photo
            WHERE trashedOn IS NULL
            ORDER BY network_photo.timeCreated DESC"""
    )
    fun getPhotosPaged(): PagingSource<Int, NetworkPhoto>

    @Query(
        """SELECT * FROM network_photo
        WHERE network_photo.folder = :folder AND trashedOn IS NULL
        ORDER BY network_photo.timeCreated DESC"""
    )
    fun getFolderPhotos(folder: String): PagingSource<Int, NetworkPhoto> // TODO include userId in query

    @Query(
        """
        SELECT folder, id, userId, COUNT(id) as photoCount FROM network_photo
        WHERE folder <> '' AND trashedOn is null
        GROUP BY folder HAVING timeCreated = MAX(timeCreated)
        ORDER BY
            CASE WHEN :ascending <> 0 THEN folder END ASC,
            CASE WHEN :ascending = 0 THEN folder END DESC
        """
    )
    fun getFolders(ascending: Boolean): Flow<List<NetworkFolder>>

    @Query(
        """SELECT *, 
              CAST((strftime('%s','now') - network_photo.timeCreated) / (3600 * 24 * 365) AS INTEGER) AS yearOffset 
        FROM network_photo
        WHERE CASE 
            WHEN :photoType = 'Personal' THEN (userId IS NOT NULL)
            WHEN :photoType = 'Family' THEN (userId IS NULL)
            ELSE 1
        END
        AND yearOffset BETWEEN :minYearsAgo AND :maxYearsAgo
        AND ABS(strftime('%j', 'now') - strftime('%j', datetime(network_photo.timeCreated, 'unixepoch'))) <= 3
        AND trashedOn is null
        ORDER BY yearOffset ASC, network_photo.timeCreated DESC"""
    )
    fun getPhotosGroupedByYearsAgo(
        photoType: PhotoType,
        minYearsAgo: Int = 1,
        maxYearsAgo: Int = 10
    ): Flow<List<NetworkPhotoWithYearOffset>>

    @Query("SELECT * FROM network_photo WHERE trashedOn IS NOT NULL ORDER BY trashedOn DESC")
    fun getTrashedPhotos(): Flow<List<NetworkPhoto>>

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
    suspend fun updatePartials(events: Collection<PhotoEventLog>, eventLogId: Long) {
        for (event in events) {
            if (event.data != null) {
                val photo: NetworkPhoto =
                    Json.decodeFromString(event.data.toByteArray().toString(Charsets.UTF_8))
                insert(photo)
            } else {
                delete(event.photoId)
            }
        }

        updateEventLogId(eventLogId)
    }

    @Transaction
    suspend fun replaceAll(
        list: Collection<NetworkPhoto>,
        eventLogId: Long
    ) {
        deleteAll()
        insert(list)
        updateEventLogId(eventLogId)
    }

    @Query("DELETE FROM network_photo")
    suspend fun deleteAll()
}
