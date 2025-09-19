package net.theluckycoder.familyphotos.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.theluckycoder.familyphotos.data.local.db.FavoritePhotosDao
import net.theluckycoder.familyphotos.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.data.model.ExifData
import net.theluckycoder.familyphotos.data.model.db.LocalPhoto
import net.theluckycoder.familyphotos.data.model.db.NetworkFolder
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.data.model.db.isVideo
import net.theluckycoder.familyphotos.data.model.db.isPublic
import net.theluckycoder.familyphotos.data.remote.PhotosService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Needed for WorkManager
class ServerRepository @Inject constructor(
    @param:ApplicationContext
    private val context: Context,
    private val photosService: Lazy<PhotosService>,
    private val networkPhotosDao: NetworkPhotosDao,
    private val localPhotosDao: LocalPhotosDao,
    private val favoritePhotosDao: FavoritePhotosDao,
) {

    enum class DownloadResponse {
        SUCCESSFUL,
        UNSUCCESSFUL,
        NOT_LOGGED_IN,
        FULL_DOWNLOAD_NEEDED
    }

    suspend fun downloadPartialPhotos(): DownloadResponse {
        val lastSyncedEventLogId = networkPhotosDao.getEventLogId()
        Log.i("ServerRepository", "Last synced event id: $lastSyncedEventLogId")

        val response = photosService.get().getEventLogsList(lastSyncedEventLogId)

        if (!response.isSuccessful) {
            return when (response.code()) {
                401 -> DownloadResponse.NOT_LOGGED_IN // UNAUTHORIZED
                409 -> DownloadResponse.FULL_DOWNLOAD_NEEDED // CONFLICT
                else -> DownloadResponse.UNSUCCESSFUL
            }
        } else {
            val partialPhotos = response.body()!!
            if (partialPhotos.events.isNotEmpty()) {
                networkPhotosDao.updatePartials(partialPhotos.events, partialPhotos.eventLogId)
            }

            Log.i(
                "ServerRepository",
                "Downloaded partial photos. EventLogId: ${partialPhotos.eventLogId}"
            )

            return DownloadResponse.SUCCESSFUL
        }
    }

    suspend fun downloadAllPhotos(): DownloadResponse = coroutineScope {
        val service = photosService.get()
        val photosAsync = async { service.getFullPhotosList() }

        val favoritePhotosResponse = service.getFavorites()
        favoritePhotosResponse.errorBody()
            ?.let { Log.e("ServerRepository", "Failed to get favorites ${it.string()}") }

        val photosResponse = photosAsync.await()

        if (photosResponse.isSuccessful) {
            val fullPhotos = photosResponse.body()!!

            if (fullPhotos.photos.isNotEmpty()) {
                networkPhotosDao.replaceAll(fullPhotos.photos, fullPhotos.eventLogId)
                Log.i(
                    "ServerRepository",
                    "Downloaded all server photos. EventLogId: ${fullPhotos.eventLogId}"
                )

                // Only insert favorite photos after downloading all photos
                favoritePhotosResponse.body()?.let { favoritePhotos ->
                    favoritePhotosDao.replaceAll(favoritePhotos)
                }

                return@coroutineScope DownloadResponse.SUCCESSFUL
            }
        }

        photosResponse.errorBody()
            ?.let { Log.e("ServerRepository", "Failed to get photos ${it.string()}") }

        DownloadResponse.UNSUCCESSFUL
    }

    suspend fun trashNetworkPhoto(photoId: Long, trash: Boolean): Boolean {
        val service = photosService.get()
        val response = if (trash) service.trashPhoto(photoId) else service.unTrashPhoto(photoId)
        val successful = response.isSuccessful

        if (successful) {
            Log.d("PhotosListRepository", "Trashed photo ($trash): $photoId")
            networkPhotosDao.insert(response.body()!!)
        }

        return successful
    }

    suspend fun deleteNetworkPhoto(photoId: Long): Boolean {
        val response = photosService.get().deletePhoto(photoId)
        val successful = response.isSuccessful

        if (successful) {
            Log.d("PhotosListRepository", "Deleting photo $photoId")
            networkPhotosDao.delete(photoId)
        }

        return successful
    }

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

    suspend fun getExifData(photo: NetworkPhoto): ExifData? {
        return photosService.get().getPhotoExif(photo.id).body()?.run {
            ExifData(this)
        }
    }

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

    suspend fun movePhotos(
        photos: List<NetworkPhoto>,
        makePublic: Boolean,
        newFolderName: String?,
    ): Boolean {
        val response = photosService.get().movePhotos(
            photoId = photos.map { it.id },
            makePublic = makePublic,
            newFolderName = newFolderName
        )

        val changedPhotos = response.body()
        Log.d("Moving Photos", response.errorBody()?.string().orEmpty())
        if (!response.isSuccessful || changedPhotos == null)
            return false

        networkPhotosDao.insert(changedPhotos)
        return true
    }

    suspend fun updateFavorite(photo: NetworkPhoto, add: Boolean) {
        val response = if (add) {
            photosService.get().addFavorite(photo.id)
        } else {
            photosService.get().removeFavorite(photo.id)
        }

        if (response.isSuccessful) {
            if (add)
                favoritePhotosDao.addFavorite(photo.id)
            else
                favoritePhotosDao.removeFavorite(photo.id)
        } else {
            Log.e(
                "ServerRepository",
                "Failed to add/remove favorite: " + response.errorBody()?.string()
            )
        }
    }

    suspend fun renameNetworkFolder(
        folder: NetworkFolder,
        makePublic: Boolean,
        newName: String?
    ): Boolean {
        val response = photosService.get().renameFolder(
            isPublic = folder.isPublic,
            folderName = folder.name,
            targetMakePublic = makePublic,
            targetFolderName = newName
        )
        Log.d("ServerRepository", "Renamed folder ${folder.name}")

        val changedPhotos = response.body()
        if (!response.isSuccessful || changedPhotos == null) {
            Log.e("ServerRepository", response.errorBody()?.string().orEmpty())
            return false
        }

        networkPhotosDao.insert(changedPhotos)
        Log.d("ServerRepository", "Updated moved folder photos (${changedPhotos.size})")
        return true
    }

    suspend fun getDuplicates(): List<List<NetworkPhoto>>? {
        val response = photosService.get().getDuplicates()
        response.body()?.let { list ->
            return list.map { ids ->
                ids.mapNotNull { id -> networkPhotosDao.findById(id) }
            }
        }

        return null
    }
}
