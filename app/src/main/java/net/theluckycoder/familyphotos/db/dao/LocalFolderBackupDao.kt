package net.theluckycoder.familyphotos.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.model.LocalFolderToBackup

@Dao
interface LocalFolderBackupDao {

    @Query("SELECT name from backup_local_folders")
    fun getAll(): Flow<List<String>>

    @Upsert
    fun upsert(folder: LocalFolderToBackup)

    @Delete
    fun delete(folder: LocalFolderToBackup)
}