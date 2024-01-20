package net.theluckycoder.familyphotos.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.model.NetworkFolder
import net.theluckycoder.familyphotos.model.NetworkPhoto

@Dao
abstract class NetworkPhotosDao : AbstractPhotosDao<NetworkPhoto>("network_photo") {

    @Query("SELECT * FROM network_photo WHERE id = :photoId")
    abstract fun findById(photoId: Long): Flow<NetworkPhoto?>

    @Query(
        """SELECT * FROM network_photo
            WHERE network_photo.userId = :userName
            ORDER BY network_photo.timeCreated DESC"""
    )
    abstract fun getPhotosPaged(userName: String): PagingSource<Int, NetworkPhoto>

    @Query(
        """SELECT * FROM network_photo
            WHERE network_photo.userId = :userId
            ORDER BY network_photo.timeCreated DESC"""
    )
    abstract fun getPhotos(userId: Long): List<NetworkPhoto>

    @Query("SELECT * FROM network_photo WHERE network_photo.folder = :folder ORDER BY network_photo.timeCreated ASC")
    abstract fun getFolderPhotos(folder: String): Flow<List<NetworkPhoto>>

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
        """SELECT * FROM network_photo
        WHERE network_photo.userId = :userId
        AND ROUND(network_photo.timeCreated / 3600 / 24 / 7 / 30) = ROUND(:timestamp / 3600 / 24 / 7 / 30)
        ORDER BY network_photo.timeCreated DESC
    """
    )
    abstract fun getPhotosInThisMonth(userId: String, timestamp: Long): Flow<List<NetworkPhoto>>

    @Query("""
        SELECT * FROM NETWORK_PHOTO
        WHERE network_photo.isFavorite = true
        ORDER BY network_photo.timeCreated DESC
    """)
    abstract fun getFavoritePhotos(): Flow<List<NetworkPhoto>>
}
