package net.theluckycoder.familyphotos.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import net.theluckycoder.familyphotos.db.LocalPhotosDao
import net.theluckycoder.familyphotos.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.model.LocalPhoto
import javax.inject.Inject

@ViewModelScoped
class FoldersRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val localPhotosDao: LocalPhotosDao,
    private val networkPhotosDao: NetworkPhotosDao,
) {

    val localFoldersFlow = localPhotosDao.getFolders()

    val networkFoldersFlow = networkPhotosDao.getFolders()

    fun localPhotosFromFolder(folder: String) =
        localPhotosDao.getFolderPhotos(folder)

    fun networkPhotosFromFolder(folder: String) =
        networkPhotosDao.getFolderPhotos(folder)

    suspend fun updatePhoneAlbums() {
        val photos = try {
            queryLocalPhotos()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        if (!photos.isNullOrEmpty())
            localPhotosDao.replaceAllChanged(photos)
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
                val bucketColumn = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
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

/*private fun getPhoneAlbums(): List<PhoneAlbum>? {
    val phoneAlbums = mutableListOf<PhoneAlbum>()
    val albumsNames = mutableSetOf<String>()

    // which image properties are we querying
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
    )

    // content: style URI for the "primary" external storage volume
    val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    context.contentResolver.query(
        images,
        projection,  // Which columns to return
        null,  // Which rows to return (all rows)
        null,  // Selection arguments (none)
        null // Ordering
    )?.use { cursor ->
        Log.d("Phone Albums", "query count=" + cursor.count)

        if (cursor.count == 0) return null

        val idColumn =
            cursor.getColumnIndex(MediaStore.Images.Media._ID)
        val bucketNameColumn =
            cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val displayNameColumn =
            cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
        val dateAddedColumn =
            cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

        val currentTimeSeconds = System.currentTimeMillis() / 1000

        while (cursor.moveToNext()) {
            val id = cursor.getString(idColumn).toLong()
            val bucketName = cursor.getString(bucketNameColumn)
            val displayName = cursor.getString(displayNameColumn)
            var dateAdded = cursor.getString(dateAddedColumn).toLong()

            if (dateAdded < currentTimeSeconds) // It means the dateAdded is in seconds
                dateAdded *= 1000 // we transform it in milliseconds

            val contentUri = ContentUris.withAppendedId(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), id
            )

            // Adding a new PhonePhoto object to phonePhotos vector
            val phonePhoto = LocalPhoto(
                id = id,
                folder = bucketName,
                uri = contentUri,
                name = displayName,
                timeCreated = dateAdded
            )

            if (albumsNames.contains(bucketName)) {
                phoneAlbums.find { it.name == bucketName }?.photos?.add(phonePhoto)
            } else {
                Log.d(
                    "Phone Albums",
                    "A new album was created => $bucketName"
                )
                val album = PhoneAlbum(
                    coverPhotoId = phonePhoto.id,
                    name = bucketName,
                )
                album.photos += phonePhoto
                phoneAlbums += album
                albumsNames += bucketName
            }
        }
    }

    phoneAlbums.forEach { album ->
        album.photos.sortByDescending { it.timeCreated }
    }

    return phoneAlbums.sortedBy { it.name }
}*/
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
    val bucketName = getString(bucketNameColumn)
    val displayName = getString(displayNameColumn)
    val mimeType = getStringOrNull(mimeTypeColumn)
    var dateAdded = getLongOrNull(dateTakenColumn) ?: getLong(dateAddedColumn)

    val currentTimeSeconds = System.currentTimeMillis() / 1000

    if (dateAdded < currentTimeSeconds) // It means the dateAdded is in seconds
        dateAdded *= 1000 // we transform it in milliseconds

    val contentUriId = ContentUris.withAppendedId(contentUri, id)

    return LocalPhoto(
        id = id,
        folder = bucketName,
        uri = contentUriId,
        name = displayName,
        timeCreated = dateAdded,
        mimeType = mimeType,
    )
}
