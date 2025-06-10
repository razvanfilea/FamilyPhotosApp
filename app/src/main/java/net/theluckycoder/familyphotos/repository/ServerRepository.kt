package net.theluckycoder.familyphotos.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.theluckycoder.familyphotos.db.dao.LocalPhotosDao
import net.theluckycoder.familyphotos.db.dao.NetworkPhotosDao
import net.theluckycoder.familyphotos.model.ExifData
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkFolder
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.isPublic
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.network.service.PhotosService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Needed for WorkManager
class ServerRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val photosService: Lazy<PhotosService>,
    private val networkPhotosDao: NetworkPhotosDao,
    private val localPhotosDao: LocalPhotosDao,
) {

    enum class PingResponse {
        SUCCESSFUL,
        UNSUCCESSFUL,
        NOT_LOGGED_IN
    }

    suspend fun pingServer(): PingResponse {
        val response = photosService.get().ping()
        return when {
            response.code() == 401 -> PingResponse.NOT_LOGGED_IN // UNAUTHORIZED
            response.isSuccessful -> PingResponse.SUCCESSFUL
            else -> PingResponse.UNSUCCESSFUL
        }
    }

    suspend fun downloadAllPhotos() = coroutineScope {
        val service = photosService.get()
        val photosAsync = async { service.getPhotosList() }

        val favoritePhotosResponse = service.getFavorites()
        val photosResponse = photosAsync.await()

        if (favoritePhotosResponse.isSuccessful && photosResponse.isSuccessful) {
            val favorites = (favoritePhotosResponse.body() ?: emptyList()).toSet()
            val photos = (photosResponse.body() ?: emptyList()).map {
                NetworkPhoto(
                    id = it.id,
                    userId = it.userId,
                    name = it.name,
                    timeCreated = it.createdAt,
                    fileSize = it.fileSize,
                    folder = it.folder,
                    isFavorite = favorites.contains(it.id),
                )
            }

            if (photos.isNotEmpty()) {
                networkPhotosDao.replaceAll(photos)
                Log.i("ServerRepository", "Downloaded all server photos")
                return@coroutineScope true
            }
        }

        false
    }

    suspend fun deleteNetworkPhoto(photoId: Long): Boolean {
        val successful = photosService.get().deletePhoto(photoId).isSuccessful

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
            localPhotosDao.insert(localPhoto)

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
        fileToUpload: File? = null,
    ): Boolean {
        val bytes = fileToUpload?.readBytes()
            ?: context.contentResolver.openInputStream(localPhoto.uri)?.buffered()?.use {
                it.readBytes()
            }

        val requestFile: RequestBody = bytes!!
            .toRequestBody(
                context.contentResolver.getType(localPhoto.uri)!!.toMediaTypeOrNull(),
                0, bytes.size
            )

        // MultipartBody.Part is used to send also the actual file name
        val name = fileToUpload?.name ?: localPhoto.name
        val fileBody = MultipartBody.Part.createFormData("file", name, requestFile)

        val response = photosService.get().uploadPhoto(
            timeCreated = localPhoto.timeCreated.toString(),
            file = fileBody,
            makePublic = public,
            folderName = uploadFolder,
        )

        response.body()?.let { networkPhoto ->
            networkPhotosDao.insert(networkPhoto)
            localPhotosDao.update(localPhoto.copy(networkPhotoId = networkPhoto.id))
            return true
        }

        Log.e("Error uploading ${localPhoto.id}", response.errorBody()?.string().orEmpty())

        return false
    }

    suspend fun changePhotoLocation(
        photo: NetworkPhoto,
        makePublic: Boolean,
        newFolderName: String?,
    ): Boolean {
        val response = photosService.get().changePhotoLocation(
            photoId = photo.id,
            makePublic = makePublic,
            newFolderName = newFolderName
        )

        val changedPhoto = response.body()
        Log.d("Moving Photos", response.errorBody()?.string().orEmpty())
        if (!response.isSuccessful || changedPhoto == null)
            return false

        networkPhotosDao.update(changedPhoto)
        return true
    }

    suspend fun updateFavorite(photo: NetworkPhoto, add: Boolean) {
        val response = if (add) {
            photosService.get().addFavorite(photo.id)
        } else {
            photosService.get().removeFavorite(photo.id)
        }

        if (response.isSuccessful) {
            networkPhotosDao.update(photo.copy(isFavorite = add))
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

        val changedPhotos = response.body()
        Log.d("Moving Photos", response.errorBody()?.string().orEmpty())
        if (!response.isSuccessful || changedPhotos == null)
            return false

        networkPhotosDao.insertOrReplace(changedPhotos)
        return true
    }
}
