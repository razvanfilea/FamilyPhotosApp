package net.theluckycoder.familyphotos.workers

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun WorkManager.enqueueBackupWorkerOnce() {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.NOT_ROAMING)
        .build()

    val request = OneTimeWorkRequestBuilder<BackupWorker>()
        .setConstraints(constraints)
        .build()

    enqueueUniqueWork("backup_once", ExistingWorkPolicy.KEEP, request)
}

fun WorkManager.enqueueUploadWorker() {
    val constraints = Constraints.Builder()
        .setRequiresStorageNotLow(true)
        .setRequiredNetworkType(NetworkType.NOT_ROAMING)
        .build()

    val request = OneTimeWorkRequestBuilder<UploadWorker>()
        .setConstraints(constraints)
        .addTag(UploadWorker.TAG)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
        .build()

    beginUniqueWork(UploadWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        .enqueue()
}
