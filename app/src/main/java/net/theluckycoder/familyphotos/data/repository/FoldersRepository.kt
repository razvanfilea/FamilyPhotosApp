package net.theluckycoder.familyphotos.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import net.theluckycoder.familyphotos.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.data.model.LocalFolder
import net.theluckycoder.familyphotos.data.model.LocalPhoto
import net.theluckycoder.familyphotos.data.model.NetworkFolder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Needed for WorkManager
class FoldersRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val localPhotosDao: LocalPhotosDao,
    private val networkPhotosDao: NetworkPhotosDao,
) {

    fun localFoldersFlow(ascending: Boolean): Flow<List<LocalFolder>> =
        localPhotosDao.getFolders(ascending)

    fun networkFoldersFlow(ascending: Boolean): Flow<List<NetworkFolder>> =
        networkPhotosDao.getFolders(ascending)

    fun localPhotosFromFolder(folder: String, count: Int) =
        localPhotosDao.getFolderPhotos(folder, count)

    fun localPhotosFromFolderPaged(folder: String) =
        localPhotosDao.getFolderPhotosPaged(folder)

    fun networkPhotosFromFolderPaged(folder: String) =
        networkPhotosDao.getFolderPhotos(folder)

    suspend fun updatePhoneAlbums() {
        val photos = try {
            queryLocalPhotos()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        if (!photos.isNullOrEmpty())
            localPhotosDao.replaceAll(photos)
    }

    private fun queryLocalPhotos(): List<LocalPhoto> {
        val photos = mutableListOf<LocalPhoto>()

        // which image properties are we querying
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_TAKEN,
        )

        // content: style URI for the "primary" external storage volume
        val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videos = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        fun query(contentUri: Uri) {
            context.contentResolver.query(
                contentUri,
                projection,  // Which columns to return
                null,  // Which rows to return (all rows)
                null,  // Selection arguments (none)
                null // Ordering
            )?.use { cursor ->
                Log.d("Phone Albums", "query count=" + cursor.count)

                if (cursor.count == 0) return

                val idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val bucketColumn =
                    cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val displayNameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeTypeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val dateTakenColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)

                while (cursor.moveToNext()) {
                    photos += cursor.parseUriToLocalImage(
                        contentUri,
                        idColumn,
                        bucketColumn,
                        displayNameColumn,
                        mimeTypeColumn,
                        dateAddedColumn,
                        dateTakenColumn
                    )
                }
            }
        }

        query(images)
        query(videos)

        return photos
    }
}

fun Cursor.parseUriToLocalImage(
    contentUri: Uri,
    idColumn: Int,
    bucketNameColumn: Int,
    displayNameColumn: Int,
    mimeTypeColumn: Int,
    dateAddedColumn: Int,
    dateTakenColumn: Int,
): LocalPhoto {
    val id = getLong(idColumn)
    val bucketName = getStringOrNull(bucketNameColumn) ?: Build.MODEL
    val displayName = getString(displayNameColumn)
    val mimeType = getStringOrNull(mimeTypeColumn)
    val dateAdded = getLongOrNull(dateTakenColumn)?.let { Instant.fromEpochMilliseconds(it) }
        ?: Instant.fromEpochSeconds(getLong(dateAddedColumn))

    val contentUriId = ContentUris.withAppendedId(contentUri, id)

    return LocalPhoto(
        id = id,
        folder = bucketName,
        uri = contentUriId,
        name = displayName,
        timeCreated = dateAdded.toEpochMilliseconds() / 1000,
        mimeType = mimeType,
    )
}
