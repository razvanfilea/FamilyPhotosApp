package net.theluckycoder.familyphotos.core.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.core.data.model.SharedUploadFile
import net.theluckycoder.familyphotos.core.data.model.db.UploadQueueEntry

@Dao
internal interface UploadQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<UploadQueueEntry>)

    @Query("SELECT * FROM upload_queue WHERE retryCount < maxRetries ORDER BY isManualUpload DESC, retryCount DESC, createdAt ASC LIMIT 1")
    suspend fun getNextPending(): UploadQueueEntry?

    @Query("UPDATE upload_queue SET retryCount = retryCount + 1 WHERE id = :entryId")
    suspend fun incrementRetryCount(entryId: Long)

    @Query("UPDATE upload_queue SET retryCount = 0 WHERE retryCount >= maxRetries")
    suspend fun resetFailedRetries()

    @Query("DELETE FROM upload_queue WHERE id = :entryId")
    suspend fun deleteById(entryId: Long)

    @Query("DELETE FROM upload_queue WHERE newFolderName = :folderName AND isManualUpload = 0")
    suspend fun deleteByFolder(folderName: String)

    @Query("UPDATE upload_queue SET folderId = :folderId, newFolderName = NULL WHERE newFolderName = :folderName")
    suspend fun convertNewFolderToExisting(folderName: String, folderId: Long)

    @Query("DELETE FROM upload_queue WHERE isManualUpload = 1")
    suspend fun deleteManualUploads()

    @Query("DELETE FROM upload_queue")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSharedFiles(files: List<SharedUploadFile>)

    @Transaction
    suspend fun insertSharedUploads(files: List<SharedUploadFile>, entries: List<UploadQueueEntry>) {
        insertSharedFiles(files)
        insertAll(entries)
    }

    @Query("SELECT * FROM shared_upload WHERE fileName = :fileName")
    suspend fun getSharedFile(fileName: String): SharedUploadFile?

    @Query("SELECT fileName FROM shared_upload")
    suspend fun getSharedFileNames(): List<String>

    // Cascades to the queue entries
    @Query("DELETE FROM shared_upload WHERE fileName = :fileName")
    suspend fun deleteSharedFile(fileName: String)

    @Query("DELETE FROM shared_upload")
    suspend fun deleteAllSharedFiles()

    @Query("SELECT COUNT(*) FROM upload_queue WHERE retryCount < maxRetries")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM upload_queue ORDER BY createdAt ASC")
    fun getAllFlow(): Flow<List<UploadQueueEntry>>
}
