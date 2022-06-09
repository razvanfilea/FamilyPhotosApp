package net.theluckycoder.familyphotos.db

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
            WHERE network_photo.ownerUserId = :userId
            ORDER BY network_photo.timeCreated DESC"""
    )
    abstract fun getPhotosPaged(userId: Long): PagingSource<Int, NetworkPhoto>

    @Query(
        """SELECT * FROM network_photo
            WHERE network_photo.ownerUserId = :userId
            ORDER BY network_photo.timeCreated DESC"""
    )
    abstract fun getPhotos(userId: Long): List<NetworkPhoto>

    @Query("SELECT * FROM network_photo WHERE network_photo.folder = :folder ORDER BY network_photo.timeCreated ASC")
    abstract fun getFolderPhotos(folder: String): Flow<List<NetworkPhoto>>

    @Query(
        """SELECT folder, id, ownerUserId, COUNT(id) FROM network_photo 
        WHERE folder <> '' GROUP BY folder
        ORDER BY network_photo.folder ASC"""
    )
    abstract fun getFolders(): Flow<List<NetworkFolder>>

    @Query(
        """SELECT * FROM network_photo
        WHERE ownerUserId = :userId
        AND ROUND(timeCreated / 1000 / 3600 / 24) = ROUND(:timestamp / 1000 / 3600 / 24)
        ORDER BY network_photo.timeCreated DESC
    """
    )
    abstract suspend fun getPhotosOnThisDay(userId: Long, timestamp: Long): List<NetworkPhoto>
}
