package net.theluckycoder.familyphotos.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.model.NetworkFolder
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.NetworkPhotoWithYearOffset

@Dao
abstract class NetworkPhotosDao : AbstractPhotosDao<NetworkPhoto>("network_photo") {

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    abstract fun findById(photoId: Long): Flow<NetworkPhoto?>

    @Query(
        """SELECT * FROM network_photo
            ORDER BY network_photo.timeCreated DESC"""
    )
    abstract fun getPhotosPaged(): PagingSource<Int, NetworkPhoto>

    @Query("""
       SELECT * FROM network_photo
        WHERE network_photo.folder = :folder
        ORDER BY network_photo.timeCreated DESC
    """)
    abstract fun getFolderPhotos(folder: String): PagingSource<Int, NetworkPhoto>

    @Query(
        """SELECT folder, id, userId, COUNT(id) FROM network_photo 
        WHERE folder <> '' GROUP BY folder
        ORDER BY network_photo.folder ASC"""
    )
    abstract fun getFolders(): Flow<List<NetworkFolder>>

    @Query(
        """SELECT * FROM network_photo
        WHERE network_photo.userId = :userId
        AND ROUND(network_photo.timeCreated / 3600 / 24 / 7) = ROUND(:timestamp / 3600 / 24 / 7)
        ORDER BY network_photo.timeCreated DESC
    """
    )
    abstract fun getPhotosInThisWeek(userId: String, timestamp: Long): Flow<List<NetworkPhoto>>

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
    abstract fun getPhotosGroupedByYearsAgo(
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
    abstract fun getFavoritePhotosPaged(): PagingSource<Int, NetworkPhoto>
}
