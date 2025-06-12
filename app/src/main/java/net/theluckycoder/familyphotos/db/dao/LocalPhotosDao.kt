package net.theluckycoder.familyphotos.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.model.LocalFolder
import net.theluckycoder.familyphotos.model.LocalPhoto

@Dao
abstract class LocalPhotosDao : AbstractPhotosDao<LocalPhoto>() {

    @Query("""
        UPDATE local_photo SET networkPhotoId = 0
        WHERE networkPhotoId <> 0 AND
        networkPhotoId NOT IN (SELECT id FROM network_photo)"""
    )
    abstract suspend fun removeMissingNetworkReferences()

    @Query("SELECT * FROM local_photo WHERE id = :photoId")
    abstract fun findById(photoId: Long): Flow<LocalPhoto?>

    @Query(
        """
        SELECT folder, id, uri, COUNT(id) FROM local_photo
        WHERE folder <> '' GROUP BY folder HAVING timeCreated = MAX(timeCreated)
        ORDER BY folder ASC"""
    )
    abstract fun getFolders(): Flow<List<LocalFolder>>

    @Query("SELECT * FROM local_photo WHERE local_photo.folder = :folder ORDER BY local_photo.timeCreated DESC LIMIT :count")
    abstract fun getFolderPhotos(folder: String, count: Int): List<LocalPhoto>

    @Query("SELECT * FROM local_photo WHERE local_photo.folder = :folder ORDER BY local_photo.timeCreated DESC")
    abstract fun getFolderPhotosPaged(folder: String): PagingSource<Int, LocalPhoto>

    @Query("SELECT * FROM local_photo WHERE local_photo.networkPhotoId = :networkPhotoId")
    abstract suspend fun findByNetworkId(networkPhotoId: Long): LocalPhoto?

    @Delete
    protected abstract fun delete(photos: Collection<LocalPhoto>)

    @Query("DELETE FROM local_photo WHERE id NOT IN (SELECT id FROM temp_photo_ids)")
    abstract override suspend fun deleteNotInTempTable()
}
