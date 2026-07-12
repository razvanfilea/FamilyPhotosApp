package net.theluckycoder.familyphotos.core.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.core.data.model.db.FavoriteNetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.db.MonthSummary
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto

@Dao
internal interface FavoritePhotosDao {

    @Query("SELECT EXISTS(SELECT * FROM favorite_network_photo WHERE photoId = :photoId)")
    fun isFavorite(photoId: Long): Flow<Boolean>

    @Query(
        """
        SELECT p.* FROM network_photo p
        JOIN favorite_network_photo ON p.id = favorite_network_photo.photoId
        WHERE p.trashedOn IS NULL
        ORDER BY p.timeCreated DESC
    """
    )
    fun getFavoritePhotosPaged(): PagingSource<Int, NetworkPhoto>

    @Query("INSERT INTO favorite_network_photo VALUES (:photoId)")
    suspend fun addFavorite(photoId: Long)

    @Query("DELETE FROM favorite_network_photo WHERE photoId = :photoId")
    suspend fun removeFavorite(photoId: Long)

    @Insert
    suspend fun insertAll(list: Collection<FavoriteNetworkPhoto>)

    @Transaction
    suspend fun replaceAll(list: Collection<Long>) {
        deleteAll()
        insertAll(list.map { FavoriteNetworkPhoto(it) })
    }

    @Query(
        """
        SELECT MAX(p.timeCreated) as timeCreated,
               p.id as coverPhotoId,
               COUNT(*) as photoCount
        FROM network_photo p
        JOIN favorite_network_photo ON p.id = favorite_network_photo.photoId
        WHERE p.trashedOn IS NULL
        GROUP BY strftime('%Y-%m', datetime(p.timeCreated, 'unixepoch', 'localtime'))
        ORDER BY p.timeCreated DESC
    """
    )
    fun getMonthSummaries(): Flow<List<MonthSummary>>

    @Query("DELETE FROM favorite_network_photo")
    suspend fun deleteAll()
}