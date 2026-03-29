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
import com.google.common.collect.Multimaps.index
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.local.db.UploadQueueDao
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import java.io.File
import java.net.ConnectException

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
    private val uploadQueueDao: UploadQueueDao,
) : CoroutineWorker(context, workerParams) {

    private val notificationId = id.hashCode()
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

        var successCount = 0
        var failCount = 0

        val result = try {
            while (true) {
                val entry = uploadQueueDao.getNextPending() ?: break
                val pending = uploadQueueDao.getPendingCountFlow().first()
                val total = successCount + pending

                val localPhoto = photosRepository.getLocalPhotoFlow(entry.localPhotoId).first()
                if (localPhoto == null || localPhoto.isSavedToCloud) {
                    uploadQueueDao.deleteById(entry.id)
                    continue
                }

                setForeground(
                    createForegroundInfo("Uploaded $successCount/$total files", successCount, total)
                )

                val success = try {
                    serverRepository.uploadFile(localPhoto, entry.makePublic, entry.uploadFolder)
                } catch (e: ConnectException) {
                    return Result.retry()
                } catch (e: Exception) {
                    Log.e(TAG, "Caught exception while uploading ${entry.localPhotoId}", e)
                    false
                }

                if (success) {
                    uploadQueueDao.deleteById(entry.id)
                    successCount++
                } else {
                    uploadQueueDao.incrementRetryCount(entry.id)
                    failCount++
                }
            }

            createSuccessNotification(successCount, failCount)

            Result.success()
        } catch (_: CancellationException) {
            createFailNotification(FailReason.Cancelled)
            Result.failure()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, e.stackTraceToString())
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
        const val UNIQUE_WORK_NAME = "upload_work"
    }
}
