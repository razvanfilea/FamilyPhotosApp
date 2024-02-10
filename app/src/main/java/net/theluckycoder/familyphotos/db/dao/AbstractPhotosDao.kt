package net.theluckycoder.familyphotos.db.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import net.theluckycoder.familyphotos.model.Photo

abstract class AbstractPhotosDao<T : Photo>(private val tableName: String) {

    @RawQuery
    protected abstract suspend fun executeSuspend(query: SupportSQLiteQuery): Int

    @RawQuery
    protected abstract suspend fun executeList(query: SupportSQLiteQuery): List<T>

    // endregion Execute

    // region Get

    suspend fun getAll(): List<T> = executeList(SimpleSQLiteQuery("SELECT * FROM $tableName"))

    // endregion Get

    // region Modify

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(photo: T)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplace(list: Collection<T>)

    @Transaction
    open suspend fun replaceAll(list: Collection<T>) {
        deleteAll()
        insertOrReplace(list)
    }

    @Update(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun update(photo: T)

    // endregion Modify

    // region Delete

    @Delete
    abstract suspend fun delete(photo: T)

    suspend fun delete(photoId: Long) =
        executeSuspend(SimpleSQLiteQuery("DELETE FROM $tableName WHERE id = $photoId"))

    private suspend fun deleteAll() = executeSuspend(SimpleSQLiteQuery("DELETE FROM $tableName"))

    // endregion Delete
}