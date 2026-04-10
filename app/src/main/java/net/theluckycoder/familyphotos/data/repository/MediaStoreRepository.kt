package net.theluckycoder.familyphotos.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import net.theluckycoder.familyphotos.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.db.isVideo
import net.theluckycoder.familyphotos.data.remote.PhotosService
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Android MediaStore operations (ContentResolver).
 */
@Singleton
class MediaStoreRepository @Inject constructor(
    @param:ApplicationContext
    private val context: Context,
    private val photosService: Lazy<PhotosService>,
    private val localPhotosDao: LocalPhotosDao,
) {

    suspend fun saveNetworkPhotoToStorage(networkPhoto: NetworkPhoto): LocalPhoto? {
        val body = photosService.get().downloadPhoto(networkPhoto.id)
            ?: run {
                Log.d("Share NetworkPhoto", "Failed to download file")
                return null
            }

        // Create an entry in MediaStore
        val values = ContentValues().apply {
            val relativeLocation = Environment.DIRECTORY_DCIM + File.separator + "FamilyPhotos"
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(networkPhoto.name.substringAfterLast('.'))

            put(MediaStore.MediaColumns.DISPLAY_NAME, networkPhoto.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
        }

        var uri: Uri? = null

        runCatching {
            with(context.contentResolver) {
                val contentUri =
                    if (networkPhoto.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                insert(contentUri, values)?.also {
                    uri = it // Keep uri reference so it can be removed on failure

                    openOutputStream(it)?.use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("Failed to open output stream.")

                } ?: throw IOException("Failed to create new MediaStore record.")
            }
        }.getOrElse {
            uri?.let { orphanUri ->
                // Don't leave an orphan entry in the MediaStore
                context.contentResolver.delete(orphanUri, null, null)
            }

            throw it
        }

        // Parse the entry to a LocalPhoto
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_TAKEN,
        )

        val localPhoto = context.contentResolver.query(
            uri!!,
            projection,  // Which columns to return
            null,  // Which rows to return (all rows)
            null,  // Selection arguments (none)
            null // Ordering
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val bucketColumn = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val displayNameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeTypeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val dateAddedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val dateTakenColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)

            if (!cursor.moveToFirst()) return null

            val contentUri =
                if (networkPhoto.isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                }

            cursor.parseUriToLocalImage(
                contentUri,
                idColumn,
                bucketColumn,
                displayNameColumn,
                mimeTypeColumn,
                dateAddedColumn,
                dateTakenColumn
            ).copy(networkPhotoId = networkPhoto.id)
        }

        if (localPhoto != null)
            localPhotosDao.insertOrReplace(localPhoto)

        return localPhoto
    }
}
