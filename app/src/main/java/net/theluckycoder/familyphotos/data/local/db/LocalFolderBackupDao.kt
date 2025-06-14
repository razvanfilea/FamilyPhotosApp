package net.theluckycoder.familyphotos.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.data.model.LocalFolderToBackup

@Dao
interface LocalFolderBackupDao {

    @Query("SELECT name from backup_local_folders")
    fun getAll(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(folder: LocalFolderToBackup)

    @Delete
    fun delete(folder: LocalFolderToBackup)
}