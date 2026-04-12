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
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.local.db.LocalFolderBackupDao
import net.theluckycoder.familyphotos.data.model.db.UploadQueueEntry
import net.theluckycoder.familyphotos.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.data.repository.PhotoUploadRepository
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import java.io.File
import java.net.ConnectException

@HiltWorker
class BackupAndUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val foldersRepository: FoldersRepository,
    private val photosRepository: PhotosRepository,
    private val photoUploadRepository: PhotoUploadRepository,
    private val localFolderBackupDao: LocalFolderBackupDao,
) : CoroutineWorker(context, workerParams) {

    private val notificationId = id.hashCode()

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        // Setup notification channel
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            ctx.getString(R.string.notification_channel_backup),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        NotificationManagerCompat.from(ctx).createNotificationChannel(channel)
        setForeground(createForegroundInfo(ctx.getString(R.string.notification_backup_starting), 0, 0))

        // Step 1: Scan folders if not skipped
        val skipScan = inputData.getBoolean(KEY_SKIP_FOLDER_SCAN, false)
        if (!skipScan) {
            scanFoldersAndQueue()
        }

        // Step 2: Process upload queue
        var successCount = 0
        var failCount = 0

        val result = try {
            while (true) {
                val entry = photoUploadRepository.getNextPending() ?: break
                val pending = photoUploadRepository.getPendingCountFlow().first()
                val total = successCount + pending

                val localPhoto = photosRepository.getLocalPhotoFlow(entry.localPhotoId).first()
                if (localPhoto == null || localPhoto.isSavedToCloud) {
                    photoUploadRepository.removeFromQueue(entry.id)
                    continue
                }

                setForeground(
                    createForegroundInfo(
                        ctx.getString(R.string.notification_backup_progress, successCount, total),
                        successCount,
                        total
                    )
                )
                setProgress(
                    Data.Builder()
                        .putInt(KEY_PROGRESS_CURRENT, successCount)
                        .putInt(KEY_PROGRESS_TOTAL, total)
                        .build()
                )

                val success = try {
                    photoUploadRepository.uploadFile(localPhoto, entry.makePublic, entry.uploadFolder)
                } catch (_: ConnectException) {
                    return Result.retry()
                } catch (e: Exception) {
                    Log.e(TAG, "Caught exception while uploading ${entry.localPhotoId}", e)
                    false
                }

                if (success) {
                    photoUploadRepository.removeFromQueue(entry.id)
                    successCount++
                } else {
                    photoUploadRepository.incrementRetryCount(entry.id)
                    failCount++
                }
            }

            createSuccessNotification(successCount, failCount)
            Result.success()
        } catch (_: CancellationException) {
            photoUploadRepository.clearManualUploads()
            createFailNotification(FailReason.Cancelled)
            Result.failure()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, e.stackTraceToString())
            createFailNotification(FailReason.Other, e.stackTraceToString())
            Result.failure()
        }

        return result
    }

    private suspend fun scanFoldersAndQueue() {
        Log.i(TAG, "Scanning folders for backup")
        foldersRepository.updatePhoneAlbums()

        val folderNames = localFolderBackupDao.getAll().first()
        Log.i(TAG, "Folders to backup: $folderNames")

        val entries = mutableListOf<UploadQueueEntry>()

        for (folderName in folderNames) {
            Log.i(TAG, "Scanning folder: $folderName")
            val photos = foldersRepository.localPhotosFromFolder(folderName, 1000)
                .filter { !it.isSavedToCloud }

            photos.mapTo(entries) { localPhoto ->
                UploadQueueEntry(
                    localPhotoId = localPhoto.id,
                    makePublic = false,
                    uploadFolder = folderName,
                    isManualUpload = false,
                )
            }
        }

        if (entries.isNotEmpty()) {
            Log.i(TAG, "Queuing ${entries.size} photos for upload")
            photoUploadRepository.enqueueUploads(entries)
        }
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
            successfulCount == 0 && failedCount == 0 -> return // Nothing to notify
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
        const val TAG = "backup_upload"
        const val UNIQUE_WORK_NAME_AUTOMATIC = "backup_upload_work"
        const val UNIQUE_WORK_NAME_MANUAL = "backup_upload_work_manual"
        const val KEY_SKIP_FOLDER_SCAN = "skip_folder_scan"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
    }
}
