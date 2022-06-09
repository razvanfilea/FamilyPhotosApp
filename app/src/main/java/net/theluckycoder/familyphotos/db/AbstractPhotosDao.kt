package net.theluckycoder.familyphotos.db

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import net.theluckycoder.familyphotos.model.Photo

abstract class AbstractPhotosDao<T : Photo>(private val tableName: String) {

    // region Execute
    @RawQuery
    protected abstract fun execute(query: SupportSQLiteQuery): Int

    @RawQuery
    protected abstract suspend fun executeSuspend(query: SupportSQLiteQuery): Int

    @RawQuery
    protected abstract fun executeList(query: SupportSQLiteQuery): List<T>

    // endregion Execute

    // region Get

    protected fun getAll(): List<T> = executeList(SimpleSQLiteQuery("SELECT * FROM $tableName"))

    // endregion Get

    // region Modify

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(photo: T)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertAll(list: Collection<T>)

    @Transaction
    open suspend fun replaceAll(list: Collection<T>) {
        deleteAll()
        insertAll(list)
    }

    @Update(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun update(photo: T)

    // endregion Modify

    // region Delete

    @Delete
    abstract suspend fun delete(photo: T)

    suspend fun delete(photoId: Long) =
        executeSuspend(SimpleSQLiteQuery("DELETE FROM $tableName WHERE id = $photoId"))

    private fun deleteAll() = execute(SimpleSQLiteQuery("DELETE FROM $tableName"))

    // endregion Delete
}