package net.theluckycoder.familyphotos.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
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
}