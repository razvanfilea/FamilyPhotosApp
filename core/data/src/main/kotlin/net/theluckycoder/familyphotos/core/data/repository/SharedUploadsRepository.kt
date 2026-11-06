package net.theluckycoder.familyphotos.core.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.theluckycoder.familyphotos.core.data.model.SharedUploadFile
import net.theluckycoder.familyphotos.core.data.model.SharedUploadMetadata
import java.io.File
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies files shared from other apps into app-private storage. URI grants from a share intent
 * end with the receiving activity, while uploads run later in WorkManager.
 */
@Singleton
class SharedUploadsRepository @Inject internal constructor(
    @param:ApplicationContext
    private val context: Context,
) {

    // filesDir, not cacheDir: the OS may evict the cache before the upload runs
    private val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME)

    private fun fileOf(fileName: String): File = File(directory, fileName)

    fun fileOf(sharedFile: SharedUploadFile): File = fileOf(sharedFile.fileName)

    /**
     * Copies every supported (image/video) URI. Items that fail are skipped.
     *
     * @param intentMimeType the share intent's type, used when the provider reports none
     * @param onCopied called (on a background thread) for each finished copy, so a caller
     * cancelled before this returns can still delete them
     */
    suspend fun import(
        uris: List<Uri>,
        intentMimeType: String?,
        onCopied: (SharedUploadFile) -> Unit = {},
        onProgress: (copiedBytes: Long, totalBytes: Long) -> Unit,
    ): List<SharedUploadFile> = withContext(Dispatchers.IO) {
        val fallbackMimeType = SharedUploadMetadata.concreteMimeType(intentMimeType)
        val items = uris.distinct().mapNotNull { readSourceInfo(it, fallbackMimeType) }
        val totalBytes = items.sumOf { it.size.coerceAtLeast(0L) }
        var copiedBytes = 0L
        directory.mkdirs()
        val imported = mutableListOf<SharedUploadFile>()

        try {
            items.mapNotNullTo(imported) { item ->
                ensureActive()
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mimeType)
                val copy = fileOf(UUID.randomUUID().toString() + (extension?.let { ".$it" } ?: ""))

                val copied = try {
                    val input = context.contentResolver.openInputStream(item.uri)
                        ?: throw IllegalStateException("No stream for ${item.uri}")
                    input.use { stream ->
                        copy.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val read = stream.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                copiedBytes += read
                                onProgress(copiedBytes, totalBytes)
                            }
                        }
                    }
                    true
                } catch (e: Exception) {
                    copy.delete()
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Failed to copy shared ${item.uri}", e)
                    false
                }
                if (!copied) return@mapNotNullTo null

                val timeCreated = SharedUploadMetadata.selectCaptureTime(
                    embeddedSeconds = readEmbeddedTime(copy, item.mimeType),
                    dateTakenMillis = item.dateTakenMillis,
                    lastModifiedMillis = item.lastModifiedMillis,
                    nowSeconds = System.currentTimeMillis() / 1000,
                )

                SharedUploadFile(
                    fileName = copy.name,
                    displayName = SharedUploadMetadata.ensureExtension(item.displayName, extension),
                    timeCreated = timeCreated,
                    mimeType = item.mimeType,
                ).also(onCopied)
            }
        } catch (e: CancellationException) {
            // The share is gone (its activity finished), so nothing will ever reference these
            imported.forEach { fileOf(it.fileName).delete() }
            throw e
        }
        imported
    }

    suspend fun delete(fileNames: Collection<String>) = withContext(Dispatchers.IO) {
        fileNames.forEach { fileOf(it).delete() }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        directory.deleteRecursively()
    }

    /** Marks copies as recently used, so [deleteOrphans] keeps them for another full period */
    suspend fun touch(fileNames: Collection<String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        fileNames.forEach { fileOf(it).setLastModified(now) }
    }

    /**
     * Removes copies that nothing references anymore, e.g. after the process died on the
     * upload screen. Recent files are kept, since an open upload screen may still own them
     * (a restored screen [touch]es its copies).
     */
    suspend fun deleteOrphans(referenced: Set<String>, maxAgeMillis: Long) =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - maxAgeMillis
            directory.listFiles()?.forEach { file ->
                if (file.name !in referenced && file.lastModified() < cutoff) file.delete()
            }
        }

    private class SourceInfo(
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val size: Long,
        val dateTakenMillis: Long?,
        val lastModifiedMillis: Long?,
    )

    private fun readSourceInfo(uri: Uri, fallbackMimeType: String?): SourceInfo? {
        var displayName: String? = null
        var size = -1L
        var dateTaken: Long? = null
        var lastModified: Long? = null

        try {
            // A null projection: providers reject columns they don't know
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fun long(column: String): Long? = cursor.getColumnIndex(column)
                        .takeIf { it != -1 && !cursor.isNull(it) }
                        ?.let { cursor.getLong(it) }

                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        .takeIf { it != -1 && !cursor.isNull(it) }
                        ?.let { displayName = cursor.getString(it) }
                    size = long(OpenableColumns.SIZE) ?: -1L
                    dateTaken = long(MediaStore.MediaColumns.DATE_TAKEN)
                    lastModified = long(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query shared $uri", e)
        }

        val name = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "shared"

        val mimeType = context.contentResolver.getType(uri)
            ?.takeIf { SharedUploadMetadata.isSupportedMimeType(it) }
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?.takeIf { SharedUploadMetadata.isSupportedMimeType(it) }
            ?: fallbackMimeType

        if (mimeType == null) {
            Log.w(TAG, "Skipping shared $uri: not an image or video")
            return null
        }

        return SourceInfo(uri, name, mimeType, size, dateTaken, lastModified)
    }

    private fun readEmbeddedTime(file: File, mimeType: String): Long? = try {
        if (mimeType.startsWith("video/")) {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                SharedUploadMetadata.parseVideoDate(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                )
            }
        } else {
            val exif = ExifInterface(file)
            SharedUploadMetadata.parseExifDateTime(
                dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
                zone = ZoneId.systemDefault(),
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read the capture time of ${file.name}", e)
        null
    }

    private companion object {
        const val TAG = "SharedUploads"
        const val DIRECTORY_NAME = "shared_uploads"
    }
}
