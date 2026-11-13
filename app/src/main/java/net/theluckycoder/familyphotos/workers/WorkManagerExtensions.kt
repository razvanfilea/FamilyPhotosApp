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
import net.theluckycoder.familyphotos.widget.MemoriesWidget
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

fun WorkManager.enqueueBackupAndUploadWorker(
    skipFolderScan: Boolean,
) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val inputData = Data.Builder()
        .putBoolean(BackupAndUploadWorker.KEY_SKIP_FOLDER_SCAN, skipFolderScan)
        .build()

    val request = OneTimeWorkRequestBuilder<BackupAndUploadWorker>()
        .setConstraints(constraints)
        .setInputData(inputData)
        .addTag(BackupAndUploadWorker.TAG)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .build()

    enqueueUniqueWork(
        BackupAndUploadWorker.UNIQUE_WORK_NAME_MANUAL,
        ExistingWorkPolicy.KEEP,
        request
    )
}

fun WorkManager.enqueuePeriodBackupWorker(
    useMobileData: Boolean,
    policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
) {
    val networkType = if (useMobileData) NetworkType.NOT_ROAMING else NetworkType.UNMETERED

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(networkType)
        .setRequiresBatteryNotLow(true)
        .build()

    val periodicWork = PeriodicWorkRequestBuilder<BackupAndUploadWorker>(4, TimeUnit.HOURS)
        .setConstraints(constraints)
        .addTag(BackupAndUploadWorker.TAG)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .build()

    enqueueUniquePeriodicWork(
        BackupAndUploadWorker.UNIQUE_WORK_NAME_AUTOMATIC,
        policy,
        periodicWork
    )
}

fun WorkManager.enqueueSharedActivityWorker(
    policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val periodicWork = PeriodicWorkRequestBuilder<SharedActivityWorker>(1, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    enqueueUniquePeriodicWork(SharedActivityWorker.UNIQUE_WORK_NAME, policy, periodicWork)
}

fun WorkManager.cancelSharedActivityWorker() {
    cancelUniqueWork(SharedActivityWorker.UNIQUE_WORK_NAME)
}

fun WorkManager.enqueueMemoriesWorker(
    policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
) {
    val now = LocalDateTime.now()
    var next = now.toLocalDate().atTime(MemoriesWorker.NOTIFY_HOUR, 0)
    if (!next.isAfter(now)) next = next.plusDays(1)

    val request = OneTimeWorkRequestBuilder<MemoriesWorker>()
        .setInitialDelay(Duration.between(now, next))
        .build()

    enqueueUniqueWork(MemoriesWorker.UNIQUE_WORK_NAME, policy, request)
}

fun WorkManager.cancelMemoriesWorker() {
    cancelUniqueWork(MemoriesWorker.UNIQUE_WORK_NAME)
}

/** At the start of the next slot, or [immediately] (then it re-enqueues itself for the next slot). */
fun WorkManager.enqueueMemoriesWidgetWorker(
    policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    immediately: Boolean = false,
) {
    val now = LocalDateTime.now()
    val slotStart = now.truncatedTo(ChronoUnit.HOURS).withHour(now.hour - now.hour % MemoriesWidget.REFRESH_HOURS)
    val next = slotStart.plusHours(MemoriesWidget.REFRESH_HOURS.toLong())

    val request = OneTimeWorkRequestBuilder<MemoriesWidgetWorker>()
        .apply { if (!immediately) setInitialDelay(Duration.between(now, next)) }
        .build()

    enqueueUniqueWork(MemoriesWidgetWorker.UNIQUE_WORK_NAME, policy, request)
}

fun WorkManager.cancelMemoriesWidgetWorker() {
    cancelUniqueWork(MemoriesWidgetWorker.UNIQUE_WORK_NAME)
}
