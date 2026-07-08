package net.theluckycoder.familyphotos.core.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.local.db.LocalFolderBackupDao
import net.theluckycoder.familyphotos.core.data.local.db.LocalPhotosDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkFoldersDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.core.data.model.db.LocalFolderToBackup
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.model.LocalFolder
import net.theluckycoder.familyphotos.core.data.model.LocalPhoto
import net.theluckycoder.familyphotos.core.data.model.db.MonthSummary
import net.theluckycoder.familyphotos.core.data.model.NetworkFolder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Singleton // Needed for WorkManager
class FoldersRepository @Inject internal constructor(
    @param:ApplicationContext
    private val context: Context,
    private val localPhotosDao: LocalPhotosDao,
    private val networkPhotosDao: NetworkPhotosDao,
    private val networkFoldersDao: NetworkFoldersDao,
    private val localFolderBackupDao: LocalFolderBackupDao,
    private val userDataStore: UserDataStore,
) {
    private val currentUserId: String get() = userDataStore.userId.value ?: ""

    companion object {
        private const val TAG = "MediaStoreObserver"
    }

    fun localFoldersFlow(ascending: Boolean): Flow<List<LocalFolder>> =
        localPhotosDao.getFolders(ascending)

    fun networkFoldersFlow(photoType: PhotoType, ascending: Boolean): Flow<List<NetworkFolder>> =
        networkFoldersDao.getFolders(photoType, ascending, currentUserId)

    fun localPhotosFromFolder(folder: String, count: Int) =
        localPhotosDao.getFolderPhotos(folder, count)

    fun localPhotosFromFolderPaged(folder: String) =
        localPhotosDao.getFolderPhotosPaged(folder)

    fun networkPhotosFromFolderPaged(folderId: Long) =
        networkPhotosDao.getFolderPhotos(folderId)

    fun localMonthSummariesForFolder(folder: String): Flow<List<MonthSummary>> =
        localPhotosDao.getMonthSummariesForFolder(folder)

    fun networkMonthSummariesForFolder(folderId: Long): Flow<List<MonthSummary>> =
        networkPhotosDao.getMonthSummariesForFolder(folderId)

    fun getFolderFlow(folderId: Long) = networkFoldersDao.getFolderFlow(folderId)

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

    // Backup folder operations

    fun getBackupFolders(): Flow<List<String>> =
        localFolderBackupDao.getAll()

    suspend fun addBackupFolder(folderName: String) =
        localFolderBackupDao.insert(LocalFolderToBackup(folderName))

    suspend fun removeBackupFolder(folderName: String) =
        localFolderBackupDao.delete(LocalFolderToBackup(folderName))

    suspend fun getFolderName(folderId: Long): String? =
        networkFoldersDao.getFolderName(folderId)

    fun getPendingBackupCount(): Flow<Int> = localPhotosDao.getPendingBackupCount()

    @OptIn(ExperimentalTime::class, FlowPreview::class)
    fun observeMediaStoreChanges(): Flow<Unit> = callbackFlow {
        Log.d(TAG, "Registering MediaStore ContentObserver")

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
                Log.d(TAG, "onChange: uris=${uris.size}, flags=$flags")
                launch(Dispatchers.IO) {
                    handleMediaStoreChange(uris, flags)
                }
                trySend(Unit)
            }
        }

        val imagesUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videosUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        context.contentResolver.registerContentObserver(imagesUri, true, observer)
        context.contentResolver.registerContentObserver(videosUri, true, observer)

        awaitClose {
            Log.d(TAG, "Unregistering MediaStore ContentObserver")
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.debounce(500L.milliseconds)

    private suspend fun handleMediaStoreChange(uris: Collection<Uri>, flags: Int) {
        if (uris.isEmpty() || flags == 0) {
            Log.d(TAG, "Fallback to full scan: uris empty=${uris.isEmpty()}, flags=$flags")
            updatePhoneAlbums()
            return
        }

        val ids = uris.mapNotNull { uri ->
            try {
                ContentUris.parseId(uri)
            } catch (_: Exception) {
                null
            }
        }

        if (ids.isEmpty()) {
            Log.d(TAG, "Fallback to full scan: no parseable IDs from URIs")
            updatePhoneAlbums()
            return
        }

        when {
            flags and ContentResolver.NOTIFY_DELETE != 0 -> {
                Log.d(TAG, "DELETE: ids=$ids")
                localPhotosDao.deleteByIds(ids)
            }

            flags and (ContentResolver.NOTIFY_INSERT or ContentResolver.NOTIFY_UPDATE) != 0 -> {
                Log.d(TAG, "INSERT/UPDATE: ids=$ids")
                val existingRefs = localPhotosDao.getNetworkReferencesForIds(ids)
                    .associateBy({ it.id }, { it.networkPhotoId })

                val photos = queryLocalPhotosByIds(ids)
                Log.d(TAG, "Queried ${photos.size} photos from MediaStore")

                val foundIds = photos.map { it.id }.toSet()
                val deletedIds =
                    ids.filter { it !in foundIds && localPhotosDao.findById(it) != null }
                if (deletedIds.isNotEmpty()) {
                    Log.d(TAG, "IDs no longer in MediaStore (trashed/deleted): $deletedIds")
                    localPhotosDao.deleteByIds(deletedIds)
                }

                for (photo in photos) {
                    val withRef = existingRefs[photo.id]?.let {
                        photo.copy(networkPhotoId = it)
                    } ?: photo
                    localPhotosDao.insertOrReplace(withRef)
                }
            }

            else -> {
                Log.d(TAG, "Fallback to full scan: unknown flags=$flags")
                updatePhoneAlbums()
            }
        }
    }

    private fun queryLocalPhotosByIds(ids: List<Long>): List<LocalPhoto> {
        val photos = mutableListOf<LocalPhoto>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_TAKEN,
        )

        val selection = MediaStore.MediaColumns._ID + " IN (" + ids.joinToString(",") + ")"
        val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val videos = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        fun query(contentUri: Uri) {
            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
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

@OptIn(ExperimentalTime::class)
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
