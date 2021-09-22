package net.theluckycoder.familyphotos.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.model.LocalFolder
import net.theluckycoder.familyphotos.model.LocalPhoto

@Dao
abstract class LocalPhotosDao {

    @Query("SELECT * FROM local_photo")
    protected abstract fun getAll(): List<LocalPhoto>

    @Query("SELECT folder, id, uri, COUNT(id) FROM local_photo GROUP BY local_photo.folder ORDER BY local_photo.folder ASC")
    abstract fun getFolders(): Flow<List<LocalFolder>>

    @Query("SELECT * FROM local_photo WHERE local_photo.folder = :folder ORDER BY local_photo.timeCreated DESC")
    abstract fun getFolderPhotos(folder: String): Flow<List<LocalPhoto>>

    @Query("SELECT * FROM local_photo WHERE id = :photoId")
    abstract suspend fun findById(photoId: Long): LocalPhoto?

    @Query("SELECT * FROM local_photo WHERE local_photo.networkPhotoId = :networkPhotoId")
    abstract suspend fun findByNetworkId(networkPhotoId: Long): LocalPhoto?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(photo: LocalPhoto)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract fun insertAll(list: Collection<LocalPhoto>)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun update(photo: LocalPhoto)

    @Query("DELETE FROM local_photo WHERE id = :photoId")
    abstract suspend fun delete(photoId: Long)

    @Delete
    protected abstract fun deleteAll(photos: Collection<LocalPhoto>)

    @Transaction
    open suspend fun replaceAllChanged(list: List<LocalPhoto>) {
        val currentMap = getAll().associateBy { it.id }
        val newMap = list.associateBy { it.id }

        val toInsert = newMap.filterKeys { !currentMap.containsKey(it) }
//        val toInsert = HashMap(newMap)
//        currentMap.forEach { pair ->
//            if (toInsert.containsKey(pair.key))
//                toInsert.remove(pair.key)
//        }

        val toDelete = currentMap.filterKeys { !newMap.containsKey(it) }
//        val toDelete = HashMap(currentMap)
//        newMap.forEach { pair ->
//            if (toDelete.containsKey(pair.key))
//                toDelete.remove(pair.key)
//        }

        if (toInsert.isNotEmpty())
            insertAll(toInsert.map { it.value })
        if (toDelete.isNotEmpty())
            deleteAll(toDelete.map { it.value })
    }
}
