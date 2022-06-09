package net.theluckycoder.familyphotos.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.model.LocalFolder
import net.theluckycoder.familyphotos.model.LocalPhoto

@Dao
abstract class LocalPhotosDao : AbstractPhotosDao<LocalPhoto>("local_photo") {

    @Query("SELECT * FROM local_photo WHERE id = :photoId")
    abstract fun findById(photoId: Long): Flow<LocalPhoto?>

    @Query(
        """
        SELECT folder, id, uri, COUNT(id) FROM (
            SELECT * FROM local_photo ORDER BY local_photo.timeCreated DESC
        ) WHERE folder <> '' GROUP BY folder ORDER BY folder ASC"""
    )
    abstract fun getFolders(): Flow<List<LocalFolder>>

    @Query("SELECT * FROM local_photo WHERE local_photo.folder = :folder ORDER BY local_photo.timeCreated DESC")
    abstract fun getFolderPhotos(folder: String): Flow<List<LocalPhoto>>

    @Query("SELECT * FROM local_photo WHERE local_photo.networkPhotoId = :networkPhotoId")
    abstract suspend fun findByNetworkId(networkPhotoId: Long): LocalPhoto?

    @Delete
    protected abstract fun delete(photos: Collection<LocalPhoto>)

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
            delete(toDelete.map { it.value })
    }
}
