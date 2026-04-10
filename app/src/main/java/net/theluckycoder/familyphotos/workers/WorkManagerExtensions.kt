package net.theluckycoder.familyphotos.workers

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun WorkManager.enqueueBackupAndUploadWorker(
    skipFolderScan: Boolean = false,
    networkType: NetworkType = NetworkType.UNMETERED
) {
    val constraints = Constraints.Builder()
        .setRequiresStorageNotLow(true)
        .setRequiredNetworkType(networkType)
        .build()

    val inputData = Data.Builder()
        .putBoolean(BackupAndUploadWorker.KEY_SKIP_FOLDER_SCAN, skipFolderScan)
        .build()

    val request = OneTimeWorkRequestBuilder<BackupAndUploadWorker>()
        .setConstraints(constraints)
        .setInputData(inputData)
        .addTag(BackupAndUploadWorker.TAG)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
        .build()

    enqueueUniqueWork(BackupAndUploadWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
}
