package net.theluckycoder.familyphotos.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.repository.PhotosRepository
import java.io.File
import java.io.IOException
import java.net.ConnectException
import kotlin.random.Random

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PhotosRepository,
) : CoroutineWorker(context, workerParams) {

    private val notificationId = Random.nextInt(1, Int.MAX_VALUE)
    private val backupFolder = File(applicationContext.cacheDir, "backup_temp")

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            "Backup",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        NotificationManagerCompat.from(ctx).createNotificationChannel(channel)
        setForeground(createForegroundInfo("Starting upload", 0, 0))

        val ids = inputData.getLongArray(KEY_INPUT_LIST) ?: return Result.failure()
        val userOwnerId = inputData.getLong(KEY_USER_OWNER_ID, -1).takeUnless { it == -1L }
        val uploadFolder = inputData.getString(KEY_UPLOAD_FOLDER)
            .orEmpty().trim().takeIf { it.isNotBlank() }

        val result = try {
            var total = ids.size

            ids.forEachIndexed { index, localPhotoId ->
                val localPhoto = repository.getLocalPhoto(localPhotoId).first()
                if (localPhoto == null || localPhoto.networkPhotoId != 0L) {
                    total--
                    return@forEachIndexed
                }

                setForeground(
                    createForegroundInfo("Uploaded $index/$total files", index, total)
                )
//                val mimeType = ctx.contentResolver.getType(localPhoto.uri)

                /*val fileToUpload = try {
                    if (mimeType?.startsWith("image") == true && !SKIPPED_MIME.contains(mimeType))
                        transformToHeif(localPhoto)
                    else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }*/

                repository.uploadFile(userOwnerId, localPhoto, null, uploadFolder)
            }

            Result.success()
        } catch (e: ConnectException) {
            createFailNotification(FailReason.NoInternet)
            Result.retry()
        } catch (e: IOException) {
            e.printStackTrace()
            createFailNotification(FailReason.IoError)
            Result.failure()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("Family", e.stackTraceToString())
            createFailNotification(FailReason.Other, e.stackTraceToString())
            Result.failure()
        }

        try {
            backupFolder.deleteRecursively()
        } catch (e: Exception) {
        }

        return result
    }

    /*private fun transformToHeif(localPhoto: LocalPhoto): File {
        val ctx = applicationContext
        backupFolder.mkdir()

        val bitmap = ImageDecoder.createSource(ctx.contentResolver, localPhoto.uri)
            .decodeBitmap { _, _ ->
                isMutableRequired = true // This will force the decode to avoid HARDWARE Bitmaps
            }

        val rotation = getRotation(localPhoto.uri)

        val file = File(backupFolder, "${localPhoto.name.substringBeforeLast('.')}.heic")
        HeifWriter.Builder(
            file.absolutePath,
            bitmap.width,
            bitmap.height,
            HeifWriter.INPUT_MODE_BITMAP
        ).setRotation(rotation)
            .setQuality(95)
            .build().apply {
                start()

                addBitmap(bitmap)

                stop(0)
                close()
            }

        return file
    }*/

    /*private fun getRotation(uri: Uri): Int {
        val ctx = applicationContext

        val exif = try {
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                try {
                    ExifInterface(stream)
                } catch (e: IOException) {
                    null
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }

        exif ?: return 0

        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }*/

    /*private fun HeifWriter.addExifData(exif: ExifInterface) {
        val tagsToCheck = arrayOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_FLASH,
            // GPS
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GconstraintsPS_PROCESSING_METHOD,
            // Exposure
            ExifInterface.TAG_EXPOSURE_INDEX,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_EXPOSURE_MODE,
        )

        for (tag in tagsToCheck) {
            val exifData = exif.getAttributeBytes(tag) ?: continue

            addExifData(0, exifData, 0, exifData.size)
        }
    }*/

    private fun createForegroundInfo(
        text: String,
        progress: Int,
        progressMax: Int
    ): ForegroundInfo {
        val ctx = applicationContext

        val title = ctx.getString(R.string.notification_backup_title)
        val cancel = ctx.getString(R.string.action_cancel)

        val cancelIntent = WorkManager.getInstance(ctx)
            .createCancelPendingIntent(id)
        val icon = Icon.createWithResource(ctx, R.drawable.ic_close)

        val cancelAction = Notification.Action.Builder(icon, cancel, cancelIntent).build()

        val notification = Notification.Builder(ctx, NOTIFICATION_CHANNEL)
            .setTicker(title)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(progressMax, progress, false)
            .addAction(cancelAction)
            .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun createFailNotification(failReason: FailReason, str: String? = null) {
        val ctx = applicationContext

        val title = ctx.getString(R.string.notification_backup_fail)

        val message = when (failReason) {
            FailReason.NoInternet -> R.string.notification_backup_fail_internet
            FailReason.IoError -> R.string.notification_backup_fail_io
            FailReason.Other -> R.string.notification_backup_fail_unknown
        }

        val m = str ?: ctx.getString(message)

        val notification = NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL)
            .setTicker(title)
            .setContentTitle(title)
            .setContentText(m)
            .setStyle(NotificationCompat.BigTextStyle().bigText(m))
            .setSmallIcon(R.drawable.ic_error_outline)
            .build()

        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_FAIL_ID, notification)
    }

    enum class FailReason {
        NoInternet,
        IoError,
        Other
    }

    companion object {
        private const val NOTIFICATION_FAIL_ID = -100
        private const val NOTIFICATION_CHANNEL = "backup"
        const val TAG = "upload"

        const val KEY_INPUT_LIST = "input_list"
        const val KEY_USER_OWNER_ID = "user_id"
        const val KEY_UPLOAD_FOLDER = "upload_folder"

        private val SKIPPED_MIME = arrayOf(
            "image/heif", "image/heic", "image/gif"
        )
    }
}