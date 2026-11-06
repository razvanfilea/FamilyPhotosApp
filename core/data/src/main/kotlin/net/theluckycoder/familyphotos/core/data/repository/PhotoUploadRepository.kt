package net.theluckycoder.familyphotos.core.data.repository

import android.content.ContentResolver
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
import net.theluckycoder.familyphotos.core.data.model.SharedUploadFile
import net.theluckycoder.familyphotos.core.data.model.UploadChoice
import net.theluckycoder.familyphotos.core.data.model.UploadSource
import net.theluckycoder.familyphotos.core.data.model.db.NetworkFolderEntity
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
import java.io.File
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
    private val sharedUploadsRepository: SharedUploadsRepository,
) {

    // Upload Queue Operations

    suspend fun enqueueUploads(entries: List<UploadQueueEntry>) =
        uploadQueueDao.insertAll(entries)

    suspend fun getNextPending(): UploadQueueEntry? =
        uploadQueueDao.getNextPending()

    suspend fun incrementRetryCount(entryId: Long) =
        uploadQueueDao.incrementRetryCount(entryId)

    suspend fun removeFromQueueByFolder(folderName: String) =
        uploadQueueDao.deleteByFolder(folderName)

    suspend fun enqueueSharedUploads(files: List<SharedUploadFile>, uploadChoice: UploadChoice) =
        uploadQueueDao.insertSharedUploads(
            files,
            files.map { UploadQueueEntry.forSharedFile(it, uploadChoice) },
        )

    /** Null if the shared entry or its copy is gone */
    suspend fun getSharedUploadSource(fileName: String): UploadSource? {
        val sharedFile = uploadQueueDao.getSharedFile(fileName) ?: return null
        val copy = sharedUploadsRepository.fileOf(sharedFile)
        if (!copy.exists()) return null
        return UploadSource(
            uri = Uri.fromFile(copy),
            name = sharedFile.displayName,
            timeCreated = sharedFile.timeCreated,
            mimeType = sharedFile.mimeType,
            localPhoto = null,
        )
    }

    /** Removes [entry] from the queue, along with its shared copy if it has one */
    suspend fun removeFromQueue(entry: UploadQueueEntry) {
        val sharedFileName = entry.sharedFileName
        // Explicit even for shared entries: a leftover row would make the worker loop forever
        uploadQueueDao.deleteById(entry.id)
        if (sharedFileName != null) deleteSharedUpload(sharedFileName)
    }

    /** Also removes the entry from the queue */
    private suspend fun deleteSharedUpload(fileName: String) {
        uploadQueueDao.deleteSharedFile(fileName)
        sharedUploadsRepository.delete(listOf(fileName))
    }

    // Shared uploads are always manual
    suspend fun clearManualUploads() {
        uploadQueueDao.deleteManualUploads()
        clearSharedUploads()
    }

    suspend fun clearQueue() {
        uploadQueueDao.deleteAll()
        clearSharedUploads()
    }

    /** Removes queued shared uploads and every copied file, including ones not queued yet */
    suspend fun clearSharedUploads() {
        uploadQueueDao.deleteAllSharedFiles()
        sharedUploadsRepository.deleteAll()
    }

    /** Deletes copies not in the queue that are older than a day (see [SharedUploadsRepository.deleteOrphans]) */
    suspend fun deleteOrphanedSharedFiles() =
        sharedUploadsRepository.deleteOrphans(
            referenced = uploadQueueDao.getSharedFileNames().toSet(),
            maxAgeMillis = ORPHAN_MAX_AGE_MILLIS,
        )

    suspend fun resetFailedRetries() =
        uploadQueueDao.resetFailedRetries()

    fun getPendingCountFlow(): Flow<Int> =
        uploadQueueDao.getPendingCountFlow()

    // Upload Operations

    suspend fun uploadFile(
        source: UploadSource,
        uploadChoice: UploadChoice,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): Boolean {
        val contentResolver = context.contentResolver
        val uri = source.uri
        val mediaType = (contentResolver.getType(uri) ?: source.mimeType)?.toMediaTypeOrNull()

        var contentLength = -1L
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            contentLength = uri.path?.let { File(it).length() } ?: -1L
        } else contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
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
                "Error uploading ${source.name}",
                "Cannot determine content length ($contentLength) or media type ($mediaType)"
            )
            return false
        }

        val folderId = when (uploadChoice) {
            is UploadChoice.NoFolder -> null
            is UploadChoice.Folder -> uploadChoice.folderId
            is UploadChoice.NewFolder -> {
                val existingFolder = networkFoldersDao.findByName(uploadChoice.name)
                if (existingFolder != null) {
                    uploadQueueDao.convertNewFolderToExisting(uploadChoice.name, existingFolder.id)
                    existingFolder.id
                } else {
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
        }

        val hash = calculateBlake3Hash(uri)

        val requestBody = object : RequestBody() {
            override fun contentType() = mediaType

            override fun contentLength(): Long = contentLength

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                contentResolver.openInputStream(uri)?.use { inputStream ->
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
                } ?: throw IOException("Failed to open InputStream for URI: $uri")
            }
        }

        // MultipartBody.Part is used to send also the actual file name
        val fileBody = MultipartBody.Part.createFormData("file", source.name, requestBody)

        val response = photosService.get().uploadPhoto(
            timeCreated = source.timeCreated.toString(),
            file = fileBody,
            makePublic = uploadChoice.isPublic,
            folderId = folderId,
            hash = hash,
        )

        response.body()?.let { dto ->
            val networkPhoto = dto.toEntity()
            networkPhotosDao.insert(networkPhoto)
            source.localPhoto?.let {
                localPhotosDao.insertOrReplace(it.copy(networkPhotoId = networkPhoto.id))
            }
            return true
        }

        Log.e("Error uploading ${source.name}", response.errorBody()?.string().orEmpty())

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

    private companion object {
        const val ORPHAN_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
