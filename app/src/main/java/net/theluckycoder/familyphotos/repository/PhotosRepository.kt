package net.theluckycoder.familyphotos.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import net.theluckycoder.familyphotos.db.LocalPhotosDao
import net.theluckycoder.familyphotos.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.model.LocalPhoto
import net.theluckycoder.familyphotos.model.NetworkPhoto
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
class PhotosRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val photosService: Lazy<PhotosService>,
    private val localPhotosDao: LocalPhotosDao,
    private val networkPhotosDao: NetworkPhotosDao,
) {

    suspend fun getLocalPhoto(photoId: Long) = localPhotosDao.findById(photoId)

    suspend fun getNetworkPhoto(photoId: Long) = networkPhotosDao.findById(photoId)

    suspend fun deleteNetworkPhoto(userId: Long, photoId: Long): Boolean {
        val successful = photosService.get().deletePhoto(userId, photoId).isSuccessful

        if (successful) {
            Log.d("PhotosListRepository", "Deleting $userId")
            getLocalPhotoFromNetwork(photoId)?.let { localPhoto ->
                localPhotosDao.update(localPhoto.copy(networkPhotoId = 0L))
            }

            networkPhotosDao.delete(photoId)
        }

        return successful
    }

    suspend fun saveNetworkPhotoToStorage(networkPhoto: NetworkPhoto): LocalPhoto? {

        val body = photosService.get().downloadPhoto(networkPhoto.ownerUserId, networkPhoto.id)
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
                insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.also {
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

            cursor.parseUriToLocalImage(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
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

    suspend fun uploadFile(
        ownerUserId: Long?,
        localPhoto: LocalPhoto,
        fileToUpload: File?,
        uploadFolder: String?
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

        val response = if (ownerUserId != null) {
            photosService.get().uploadPhoto(
                userId = ownerUserId,
                timeCreated = localPhoto.timeCreated,
                file = fileBody,
                folderName = uploadFolder,
            )
        } else {
            photosService.get().uploadPublicPhoto(
                timeCreated = localPhoto.timeCreated,
                file = fileBody,
                folderName = uploadFolder,
            )
        }

        response.body()?.let { networkPhoto ->
            networkPhotosDao.insert(networkPhoto)
            localPhotosDao.update(localPhoto.copy(networkPhotoId = networkPhoto.id))
            return true
        }

        return false
    }

    suspend fun changePhotoLocation(
        photo: NetworkPhoto,
        newUserOwnerId: Long?,
        newFolderName: String?,
    ): Boolean {
        val response = photosService.get().changePhotoLocation(
            photo.ownerUserId,
            photo.id,
            newUserOwnerId,
            newFolderName
        )

        val changedPhoto = response.body()
        if (!response.isSuccessful || changedPhoto == null)
            return false

        networkPhotosDao.update(changedPhoto)
        return true
    }

    suspend fun getLocalPhotoFromNetwork(networkPhotoId: Long) =
        localPhotosDao.findByNetworkId(networkPhotoId)

    suspend fun deleteLocalPhoto(photo: LocalPhoto): Boolean {
        val result = try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                context.contentResolver.delete(
                    photo.uri,
                    "${MediaStore.Images.Media._ID} = ?",
                    arrayOf(photo.id.toString())
                )
                true
            } else {
                File(photo.uri.path!!).delete()
            }
        } catch (securityException: SecurityException) {
            securityException.printStackTrace()
            false
        }

        if (result)
            localPhotosDao.delete(photo.id)

        return result
    }
}