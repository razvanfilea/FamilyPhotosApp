package net.theluckycoder.familyphotos.core.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.model.db.FolderCursor
import net.theluckycoder.familyphotos.core.data.model.NetworkFolder
import net.theluckycoder.familyphotos.core.data.model.db.NetworkFolderEntity

@Dao
internal interface NetworkFoldersDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: NetworkFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<NetworkFolderEntity>)

    @Query("DELETE FROM network_folder")
    suspend fun deleteAll()

    @Query("SELECT * FROM network_folder WHERE id = :folderId")
    fun getFolderFlow(folderId: Long): Flow<NetworkFolderEntity?>

    @Query("SELECT name FROM network_folder WHERE id = :folderId")
    suspend fun getFolderName(folderId: Long): String?

    @Query("SELECT id, latestEventId FROM network_folder WHERE ownerId IS NOT NULL AND ownerId != :currentUserId")
    suspend fun getSharedFolderCursors(currentUserId: String): List<FolderCursor>

    @Query(
        """
        SELECT nf.id AS folderId, nf.name AS folderName, nf.ownerId as userId,
               agg.coverPhotoId, agg.photoCount
        FROM network_folder nf
        INNER JOIN (
            SELECT folderId, COUNT(*) AS photoCount, MAX(timeCreated),
                   id AS coverPhotoId
            FROM network_photo
            WHERE trashedOn IS NULL
            AND CASE WHEN :photoType = 1 THEN (userId = :currentUserId)
                     WHEN :photoType = 2 THEN (userId IS NULL)
                     WHEN :photoType = 3 THEN (userId <> :currentUserId)
                     ELSE 1 END
            GROUP BY folderId
        ) agg ON agg.folderId = nf.id
        ORDER BY
            CASE WHEN :ascending <> 0 THEN nf.name END ASC,
            CASE WHEN :ascending = 0 THEN nf.name END DESC
        """
    )
    fun getFolders(photoType: PhotoType, ascending: Boolean, currentUserId: String): Flow<List<NetworkFolder>>

    @Transaction
    suspend fun replaceAll(folders: List<NetworkFolderEntity>) {
        deleteAll()
        insertAll(folders)
    }
}