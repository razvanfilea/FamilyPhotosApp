package net.theluckycoder.familyphotos.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import net.theluckycoder.familyphotos.data.model.BasicNetworkPhoto
import net.theluckycoder.familyphotos.data.model.NetworkFolder
import net.theluckycoder.familyphotos.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.NetworkPhotoWithYearOffset
import net.theluckycoder.familyphotos.data.model.PhotoEventLog

@Dao
interface NetworkPhotosDao {

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    fun findById(photoId: Long): NetworkPhoto?

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    fun findByIdFlow(photoId: Long): Flow<NetworkPhoto?>

    @Query(
        """SELECT * FROM network_photo
            ORDER BY network_photo.timeCreated DESC"""
    )
    fun getPhotosPaged(): PagingSource<Int, NetworkPhoto>

    @Query(
        """SELECT * FROM network_photo
        WHERE network_photo.folder = :folder
        ORDER BY network_photo.timeCreated DESC"""
    )
    fun getFolderPhotos(folder: String): PagingSource<Int, NetworkPhoto>

    @Query(
        """
        SELECT folder, id, userId, COUNT(id) as photoCount FROM network_photo
        WHERE folder <> ''
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
       WHERE (:userName IS NULL OR (network_photo.userId = :userName))
       AND yearOffset BETWEEN :minYearsAgo AND :maxYearsAgo
       AND ABS(strftime('%j', 'now') - strftime('%j', datetime(network_photo.timeCreated, 'unixepoch'))) <= 3
       ORDER BY yearOffset ASC, network_photo.timeCreated DESC"""
    )
    fun getPhotosGroupedByYearsAgo(
        userName: String?,
        minYearsAgo: Int = 1,
        maxYearsAgo: Int = 10
    ): Flow<List<NetworkPhotoWithYearOffset>>

    @Query(
        """
        SELECT * FROM network_photo
        WHERE network_photo.isFavorite = true
        ORDER BY network_photo.timeCreated DESC
    """
    )
    fun getFavoritePhotosPaged(): PagingSource<Int, NetworkPhoto>

    @Query("UPDATE network_photo SET isFavorite = 1 WHERE id IN (:photos)")
    suspend fun setAsFavorites(photos: Collection<Long>)

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
                val photo: BasicNetworkPhoto =
                    Json.decodeFromString(event.data.toByteArray().toString(Charsets.UTF_8))
                val isFavorite = findById(photo.id)?.isFavorite ?: false
                insert(photo.toNetworkPhoto(isFavorite))
            } else {
                delete(event.photoId)
            }
        }

        updateEventLogId(eventLogId)
    }

    @Transaction
    suspend fun replaceAll(
        list: Collection<NetworkPhoto>,
        favorites: Collection<Long>,
        eventLogId: Long
    ) {
        deleteAll()
        insert(list)
        setAsFavorites(favorites)
        updateEventLogId(eventLogId)
    }

    @Query("DELETE FROM network_photo")
    suspend fun deleteAll()
}
