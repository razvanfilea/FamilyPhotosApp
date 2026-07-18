package net.theluckycoder.familyphotos.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import blake.hash.BLAKE3
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.core.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkFoldersDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.core.data.local.db.UploadQueueDao
import net.theluckycoder.familyphotos.core.data.model.db.NetworkFolderEntity
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.model.UploadChoice
import net.theluckycoder.familyphotos.core.data.model.db.UploadQueueEntry
import net.theluckycoder.familyphotos.core.data.model.network.CreateFolderRequest
import net.theluckycoder.familyphotos.core.data.model.network.toEntity
import net.theluckycoder.familyphotos.core.data.remote.FolderService
import net.theluckycoder.familyphotos.core.data.remote.PhotosService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import javax.inject.Inject

/**
 * Handles photo upload operations and upload queue management.
 */
class PhotoUploadRepository @Inject internal constructor(
    @param:ApplicationContext
    private val context: Context,
    private val photosService: Lazy<PhotosService>,
    private val folderService: Lazy<FolderService>,
    private val networkPhotosDao: NetworkPhotosDao,
    private val networkFoldersDao: NetworkFoldersDao,
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

    // Upload Operations

    suspend fun uploadFile(
        localPhoto: LocalPhoto,
        uploadChoice: UploadChoice,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
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

        val folderId = when (uploadChoice) {
            is UploadChoice.NoFolder -> null
            is UploadChoice.Folder -> uploadChoice.folderId
            is UploadChoice.NewFolder -> {
                val response = folderService.get()
                    .createFolder(CreateFolderRequest(uploadChoice.name, uploadChoice.isPublic))

                val folderDto = response.body()
                if (folderDto == null) {
                    Log.e(
                        "Error creating folder",
                        "Folder with name: `${uploadChoice.name}` public: `${uploadChoice.isPublic}`. ${response.errorBody()}"
                    )
                    return false
                }

                networkFoldersDao.insert(
                    NetworkFolderEntity(
                        id = folderDto.id,
                        ownerId = folderDto.ownerId,
                        name = folderDto.name,
                        latestEventId = 0L,
                        createdAt = 0L,
                    )
                )
                uploadQueueDao.convertNewFolderToExisting(uploadChoice.name, folderDto.id)

                folderDto.id
            }
        }

        val hash = calculateBlake3Hash(localPhoto.uri)

        val requestBody = object : RequestBody() {
            override fun contentType() = mediaType

            override fun contentLength(): Long = contentLength

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                contentResolver.openInputStream(localPhoto.uri)?.use { inputStream ->
                    if (onProgress != null) {
                        val buffer = ByteArray(8192)
                        var bytesWritten = 0L
                        val totalBytes = contentLength
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            bytesWritten += read
                            onProgress(bytesWritten, totalBytes)
                        }
                    } else {
                        inputStream.source().use { source ->
                            sink.writeAll(source)
                        }
                    }
                } ?: throw IOException("Failed to open InputStream for URI: $localPhoto.uri")
            }
        }

        // MultipartBody.Part is used to send also the actual file name
        val fileBody = MultipartBody.Part.createFormData("file", localPhoto.name, requestBody)

        val response = photosService.get().uploadPhoto(
            timeCreated = localPhoto.timeCreated.toString(),
            file = fileBody,
            makePublic = uploadChoice.isPublic,
            folderId = folderId,
            hash = hash,
        )

        response.body()?.let { dto ->
            val networkPhoto = dto.toEntity()
            networkPhotosDao.insert(networkPhoto)
            localPhotosDao.insertOrReplace(localPhoto.copy(networkPhotoId = networkPhoto.id))
            return true
        }

        Log.e("Error uploading ${localPhoto.id}", response.errorBody()?.string().orEmpty())

        return false
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun calculateBlake3Hash(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val hasher = BLAKE3.Hasher()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    hasher.update(buffer, 0, bytesRead)
                }
                hasher.finalize().toHexString()
            }
        } catch (e: Exception) {
            Log.e("PhotoUploadRepository", "Failed to compute BLAKE3 hash for $uri", e)
            null
        }
    }
}
