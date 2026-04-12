package net.theluckycoder.familyphotos.workers

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun WorkManager.enqueueBackupAndUploadWorker(
    networkType: NetworkType,
    skipFolderScan: Boolean,
) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(networkType)
        .build()

    val inputData = Data.Builder()
        .putBoolean(BackupAndUploadWorker.KEY_SKIP_FOLDER_SCAN, skipFolderScan)
        .build()

    val request = OneTimeWorkRequestBuilder<BackupAndUploadWorker>()
        .setConstraints(constraints)
        .setInputData(inputData)
        .addTag(BackupAndUploadWorker.TAG)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .build()

    enqueueUniqueWork(
        BackupAndUploadWorker.UNIQUE_WORK_NAME_MANUAL,
        ExistingWorkPolicy.REPLACE,
        request
    )
}

fun WorkManager.enqueuePeriodBackupWorker(useMobileData: Boolean) {
    val networkType = if (useMobileData) NetworkType.NOT_ROAMING else NetworkType.UNMETERED

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(networkType)
        .setRequiresBatteryNotLow(true)
        .build()

    val periodicWork = PeriodicWorkRequestBuilder<BackupAndUploadWorker>(4, TimeUnit.HOURS)
        .setConstraints(constraints)
        .addTag(BackupAndUploadWorker.TAG)
        .build()

    enqueueUniquePeriodicWork(
        BackupAndUploadWorker.UNIQUE_WORK_NAME_AUTOMATIC,
        ExistingPeriodicWorkPolicy.UPDATE,
        periodicWork
    )
}
