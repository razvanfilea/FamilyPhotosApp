package net.theluckycoder.familyphotos.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.data.model.db.UploadQueueEntry

@Dao
interface UploadQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<UploadQueueEntry>)

    @Query("SELECT * FROM upload_queue WHERE retryCount < maxRetries ORDER BY isManualUpload DESC, createdAt ASC LIMIT 1")
    suspend fun getNextPending(): UploadQueueEntry?

    @Query("UPDATE upload_queue SET retryCount = retryCount + 1 WHERE id = :entryId")
    suspend fun incrementRetryCount(entryId: Long)

    @Query("DELETE FROM upload_queue WHERE id = :entryId")
    suspend fun deleteById(entryId: Long)

    @Query("DELETE FROM upload_queue WHERE uploadFolder = :folderName AND isManualUpload = 0")
    suspend fun deleteByFolder(folderName: String)

    @Query("SELECT COUNT(*) FROM upload_queue WHERE retryCount < maxRetries")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM upload_queue ORDER BY createdAt ASC")
    fun getAllFlow(): Flow<List<UploadQueueEntry>>
}
