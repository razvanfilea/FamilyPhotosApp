package net.theluckycoder.familyphotos.data.repository

import android.content.Context
import android.provider.OpenableColumns
import android.util.Log
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.data.local.db.UploadQueueDao
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.model.db.UploadQueueEntry
import net.theluckycoder.familyphotos.data.remote.PhotosService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles photo upload operations and upload queue management.
 */
@Singleton
class PhotoUploadRepository @Inject constructor(
    @param:ApplicationContext
    private val context: Context,
    private val photosService: Lazy<PhotosService>,
    private val networkPhotosDao: NetworkPhotosDao,
    private val localPhotosDao: LocalPhotosDao,
    private val uploadQueueDao: UploadQueueDao,
) {

    // Upload Queue Operations

    suspend fun enqueueUploads(entries: List<UploadQueueEntry>) =
        uploadQueueDao.insertAll(entries)

    suspend fun getNextPending(): UploadQueueEntry? =
        uploadQueueDao.getNextPending()

    suspend fun incrementRetryCount(entryId: Long) =
        uploadQueueDao.incrementRetryCount(entryId)

    suspend fun removeFromQueue(entryId: Long) =
        uploadQueueDao.deleteById(entryId)

    suspend fun removeFromQueueByFolder(folderName: String) =
        uploadQueueDao.deleteByFolder(folderName)

    suspend fun clearManualUploads() =
        uploadQueueDao.deleteManualUploads()

    fun getPendingCountFlow(): Flow<Int> =
        uploadQueueDao.getPendingCountFlow()

    fun getAllQueuedFlow(): Flow<List<UploadQueueEntry>> =
        uploadQueueDao.getAllFlow()

    // Upload Operations

    suspend fun uploadFile(
        localPhoto: LocalPhoto,
        public: Boolean,
        uploadFolder: String?,
    ): Boolean {
        val contentResolver = context.contentResolver
        val mediaType = contentResolver.getType(localPhoto.uri)?.toMediaTypeOrNull()

        var contentLength = -1L
        contentResolver.query(localPhoto.uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        contentLength = cursor.getLong(sizeIndex)
                    }
                }
            }

        if (contentLength == -1L && mediaType == null) {
            Log.e(
                "Error uploading ${localPhoto.id}",
                "Cannot determine content length ($contentLength) or media type ($mediaType)"
            )
            return false
        }

        val requestBody = object : RequestBody() {
            override fun contentType() = mediaType

            override fun contentLength(): Long = contentLength

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                contentResolver.openInputStream(localPhoto.uri)?.use { inputStream ->
                    inputStream.source().use { source ->
                        sink.writeAll(source)
                    }
                } ?: throw IOException("Failed to open InputStream for URI: $localPhoto.uri")
            }
        }

        // MultipartBody.Part is used to send also the actual file name
        val fileBody = MultipartBody.Part.createFormData("file", localPhoto.name, requestBody)

        val response = photosService.get().uploadPhoto(
            timeCreated = localPhoto.timeCreated.toString(),
            file = fileBody,
            makePublic = public,
            folderName = uploadFolder,
        )

        response.body()?.let { networkPhoto ->
            networkPhotosDao.insert(networkPhoto)
            localPhotosDao.insertOrReplace(localPhoto.copy(networkPhotoId = networkPhoto.id))
            return true
        }

        Log.e("Error uploading ${localPhoto.id}", response.errorBody()?.string().orEmpty())

        return false
    }
}
