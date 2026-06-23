package net.theluckycoder.familyphotos.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.model.db.NetworkFolder
import net.theluckycoder.familyphotos.data.model.db.NetworkFolderEntity

@Dao
interface NetworkFoldersDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<NetworkFolderEntity>)

    @Query("DELETE FROM network_folder")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(folders: List<NetworkFolderEntity>) {
        deleteAll()
        insertAll(folders)
    }

    @Query("SELECT * FROM network_folder")
    fun getAll(): Flow<List<NetworkFolderEntity>>

    @Query("SELECT name FROM network_folder WHERE id = :folderId")
    suspend fun getFolderName(folderId: Long): String?

    @Query(
        """
        SELECT nf.id AS folderId, nf.name AS folderName, latest.userId,
               latest.id AS coverPhotoId,
               (SELECT COUNT(*) FROM network_photo sub
                WHERE sub.folderId = nf.id AND sub.trashedOn IS NULL
                AND CASE WHEN :photoType = 1 THEN (sub.userId IS NOT NULL)
                         WHEN :photoType = 2 THEN (sub.userId IS NULL)
                         ELSE 1 END) AS photoCount
        FROM network_folder nf
        INNER JOIN network_photo latest ON latest.folderId = nf.id
            AND latest.trashedOn IS NULL
            AND CASE WHEN :photoType = 1 THEN (latest.userId IS NOT NULL)
                     WHEN :photoType = 2 THEN (latest.userId IS NULL)
                     ELSE 1 END
            AND latest.timeCreated = (
                SELECT MAX(np2.timeCreated) FROM network_photo np2
                WHERE np2.folderId = nf.id AND np2.trashedOn IS NULL
                AND CASE WHEN :photoType = 1 THEN (np2.userId IS NOT NULL)
                         WHEN :photoType = 2 THEN (np2.userId IS NULL)
                         ELSE 1 END)
        ORDER BY
            CASE WHEN :ascending <> 0 THEN nf.name END ASC,
            CASE WHEN :ascending = 0 THEN nf.name END DESC
        """
    )
    fun getFolders(photoType: PhotoType, ascending: Boolean): Flow<List<NetworkFolder>>
}