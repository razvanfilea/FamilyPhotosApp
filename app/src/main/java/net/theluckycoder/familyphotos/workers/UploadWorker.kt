package net.theluckycoder.familyphotos.workers

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.repository.PhotosRepository
import net.theluckycoder.familyphotos.repository.ServerRepository
import java.io.File
import java.io.IOException
import java.net.ConnectException
import kotlin.random.Random

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
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
        val makePublic = inputData.getBoolean(KEY_MAKE_PUBLIC, false)
        val uploadFolder = inputData.getString(KEY_UPLOAD_FOLDER)
            .orEmpty().trim().takeIf { it.isNotEmpty() }

        val result = try {
            var total = ids.size
            var failedCount = 0

            ids.forEachIndexed { index, localPhotoId ->
                val localPhoto = photosRepository.getLocalPhoto(localPhotoId).first()
                if (localPhoto == null || localPhoto.networkPhotoId != 0L) {
                    total--
                    return@forEachIndexed
                }

                setForeground(
                    createForegroundInfo("Uploaded $index/$total files", index, total)
                )

                val success = try {
                    serverRepository.uploadFile(localPhoto, makePublic, uploadFolder, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Caught exception while uploading $localPhotoId", e)
                    false
                }

                if (!success) {
                    Log.e(TAG, "Failed to upload $localPhotoId")
                    failedCount++
                }
            }

            createSuccessNotification(total - failedCount, failedCount)

            Result.success()
        } catch (e: ConnectException) {
            createFailNotification(FailReason.NoInternet)
            Result.retry()
        } catch (e: CancellationException) {
            createFailNotification(FailReason.Cancelled)
            Result.failure()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("Family", e.stackTraceToString())
            createFailNotification(FailReason.Other, e.stackTraceToString())
            Result.failure()
        }

        try {
            backupFolder.deleteRecursively()
        } catch (_: Exception) {
        }

        return result
    }

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

        val messageRes = when (failReason) {
            FailReason.Cancelled -> R.string.notification_backup_fail_cancelled
            FailReason.NoInternet -> R.string.notification_backup_fail_internet
            FailReason.Other -> R.string.notification_backup_fail_unknown
        }

        val message = str ?: ctx.getString(messageRes)

        val notification = NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL)
            .setTicker(title)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_error_outline)
            .build()

        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_FAIL_ID, notification)
        }
    }

    private fun createSuccessNotification(successfulCount: Int, failedCount: Int) {
        val ctx = applicationContext
        val title = ctx.getString(R.string.notification_backup_finished)

        val content = when {
            successfulCount == 0 -> ctx.getString(
                R.string.notification_backup_finished_desc_only_failure,
                failedCount
            )

            failedCount == 0 -> ctx.getString(
                R.string.notification_backup_finished_desc_only_success,
                successfulCount
            )

            else -> ctx.getString(
                R.string.notification_backup_finished_desc,
                successfulCount,
                failedCount
            )
        }

        val notification = NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL)
            .setTicker(title)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.ic_cloud_done_filled)
            .build()

        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_FAIL_ID, notification)
        }
    }

    enum class FailReason {
        Cancelled,
        NoInternet,
        Other
    }

    companion object {
        private const val NOTIFICATION_FAIL_ID = -100
        private const val NOTIFICATION_CHANNEL = "backup"
        const val TAG = "upload"

        const val KEY_INPUT_LIST = "input_list"
        const val KEY_MAKE_PUBLIC = "make_public"
        const val KEY_UPLOAD_FOLDER = "upload_folder"
    }
}