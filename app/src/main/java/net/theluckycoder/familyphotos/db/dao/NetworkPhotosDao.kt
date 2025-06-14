package net.theluckycoder.familyphotos.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.model.NetworkFolder
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.NetworkPhotoWithYearOffset

@Dao
interface NetworkPhotosDao {

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    fun findById(photoId: Long): Flow<NetworkPhoto?>

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
       WHERE (
          :userName IS NULL
          OR (network_photo.userId = :userName)
      )
         AND yearOffset BETWEEN :minYearsAgo AND :maxYearsAgo
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: NetworkPhoto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: Collection<NetworkPhoto>)

    @Query("DELETE FROM network_photo WHERE id = :photoId")
    fun delete(photoId: Long)

    @Transaction
    suspend fun replaceAll(list: Collection<NetworkPhoto>) {
        deleteAll()
        insert(list)
    }

    @Query("DELETE FROM network_photo")
    suspend fun deleteAll()
}
