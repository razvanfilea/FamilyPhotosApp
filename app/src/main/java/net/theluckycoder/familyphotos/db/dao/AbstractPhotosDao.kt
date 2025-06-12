package net.theluckycoder.familyphotos.db.dao

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.model.TempPhotoId

abstract class AbstractPhotosDao<T : Photo> {

    // region Modify

    @Upsert
    abstract suspend fun upsert(photo: T)

    @Upsert
    abstract suspend fun upsert(list: Collection<T>)

    @Transaction
    open suspend fun replaceAll(list: Collection<T>) {
        clearTempTable()
        upsert(list)

        val ids = list.map { TempPhotoId(it.id) }
        insertTempIds(ids)

        deleteNotInTempTable()
    }

    // endregion Modify

    // region Temp table

    @Query("DELETE FROM temp_photo_ids")
    protected abstract suspend fun clearTempTable()

    @Insert(entity = TempPhotoId::class)
    protected abstract suspend fun insertTempIds(ids: List<TempPhotoId>)

    protected abstract suspend fun deleteNotInTempTable()

    // endregion
}