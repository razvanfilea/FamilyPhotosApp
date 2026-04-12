package net.theluckycoder.familyphotos.data.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.data.model.db.LocalFolder
import net.theluckycoder.familyphotos.data.model.LocalNetworkReference
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.model.db.MonthSummary

@Dao
interface LocalPhotosDao {

    @Query("SELECT * FROM local_photo WHERE id = :photoId")
    fun findById(photoId: Long): LocalPhoto?

    @Query("SELECT * FROM local_photo WHERE id = :photoId")
    fun findByIdFlow(photoId: Long): Flow<LocalPhoto?>

    @Query(
        """
        SELECT folder, id, uri, COUNT(id) FROM local_photo
        WHERE folder <> '' GROUP BY folder HAVING timeCreated = MAX(timeCreated)
        ORDER BY
            CASE WHEN :ascending <> 0 THEN folder END ASC,
            CASE WHEN :ascending = 0 THEN folder END DESC"""
    )
    fun getFolders(ascending: Boolean): Flow<List<LocalFolder>>

    @Query("SELECT * FROM local_photo WHERE local_photo.folder = :folder ORDER BY local_photo.timeCreated DESC LIMIT :count")
    fun getFolderPhotos(folder: String, count: Int): List<LocalPhoto>

    @Query("SELECT * FROM local_photo WHERE local_photo.folder = :folder ORDER BY local_photo.timeCreated DESC")
    fun getFolderPhotosPaged(folder: String): PagingSource<Int, LocalPhoto>

    @Query(
        """
        SELECT MAX(timeCreated) as timeCreated,
               id as coverPhotoId,
               COUNT(*) as photoCount
        FROM local_photo
        WHERE folder = :folder
        GROUP BY strftime('%Y-%m', datetime(timeCreated, 'unixepoch', 'localtime'))
        ORDER BY timeCreated DESC
        """
    )
    fun getMonthSummariesForFolder(folder: String): Flow<List<MonthSummary>>

    @Query("SELECT * FROM local_photo WHERE local_photo.networkPhotoId = :networkPhotoId")
    suspend fun findByNetworkId(networkPhotoId: Long): LocalPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(photo: LocalPhoto)

    @Insert
    suspend fun insert(list: Collection<LocalPhoto>)

    // region replaceAll

    @Transaction
    suspend fun replaceAll(list: Collection<LocalPhoto>) {
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
    suspend fun getValidNetworkReferences(): List<LocalNetworkReference>

    @Query("DELETE FROM local_photo")
    fun deleteAll()

    // endregion replaceAll

    @Query(
        """
        SELECT COUNT(*) FROM local_photo
        WHERE folder IN (SELECT name FROM backup_local_folders)
        AND networkPhotoId = 0
        """
    )
    fun getPendingBackupCount(): Flow<Int>
}
