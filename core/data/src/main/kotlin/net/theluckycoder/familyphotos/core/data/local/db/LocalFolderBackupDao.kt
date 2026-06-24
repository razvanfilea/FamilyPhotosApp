package net.theluckycoder.familyphotos.core.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.core.data.model.db.LocalFolderToBackup

@Dao
internal interface LocalFolderBackupDao {

    @Query("SELECT name from backup_local_folders")
    fun getAll(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: LocalFolderToBackup)

    @Delete
    suspend fun delete(folder: LocalFolderToBackup)
}