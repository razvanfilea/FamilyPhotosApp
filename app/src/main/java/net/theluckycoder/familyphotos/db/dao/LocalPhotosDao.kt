package net.theluckycoder.familyphotos.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.model.LocalFolder
import net.theluckycoder.familyphotos.model.LocalNetworkReference
import net.theluckycoder.familyphotos.model.LocalPhoto

@Dao
abstract class LocalPhotosDao {


    @Query("SELECT * FROM local_photo WHERE id = :photoId")
    abstract fun findById(photoId: Long): Flow<LocalPhoto?>

    @Query(
        """
        SELECT folder, id, uri, COUNT(id) FROM local_photo
        WHERE folder <> '' GROUP BY folder HAVING timeCreated = MAX(timeCreated)
        ORDER BY
            CASE WHEN :ascending <> 0 THEN folder END ASC,
            CASE WHEN :ascending = 0 THEN folder END DESC"""
    )
    abstract fun getFolders(ascending: Boolean): Flow<List<LocalFolder>>

    @Query("SELECT * FROM local_photo WHERE local_photo.folder = :folder ORDER BY local_photo.timeCreated DESC LIMIT :count")
    abstract fun getFolderPhotos(folder: String, count: Int): List<LocalPhoto>

    @Query("SELECT * FROM local_photo WHERE local_photo.folder = :folder ORDER BY local_photo.timeCreated DESC")
    abstract fun getFolderPhotosPaged(folder: String): PagingSource<Int, LocalPhoto>

    @Query("SELECT * FROM local_photo WHERE local_photo.networkPhotoId = :networkPhotoId")
    abstract suspend fun findByNetworkId(networkPhotoId: Long): LocalPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplace(photo: LocalPhoto)

    @Insert
    abstract suspend fun insert(list: Collection<LocalPhoto>)

    // region replaceAll

    @Transaction
    open suspend fun replaceAll(list: Collection<LocalPhoto>) {
        val networkReferences =
            getValidNetworkReferences().associateBy({ it.id }, { it.networkPhotoId })

        deleteAll()

        val updatedList = list.map { photo ->
            networkReferences[photo.id]?.let { photo.copy(networkPhotoId = it) } ?: photo
        }
        insert(updatedList)
    }

    @Query(
        """
        SELECT id, networkPhotoId FROM local_photo
        WHERE networkPhotoId <> 0 AND
        EXISTS (
            SELECT 1 FROM network_photo np WHERE np.id = networkPhotoId
        )"""
    )
    abstract suspend fun getValidNetworkReferences(): List<LocalNetworkReference>

    @Query("DELETE FROM local_photo")
    protected abstract fun deleteAll()

    // endregion replaceAll
}
