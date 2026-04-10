package net.theluckycoder.familyphotos.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.workers.BackupAndUploadWorker
import java.util.concurrent.TimeUnit

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsStore = SettingsDataStore(context)
                val useMobileData = settingsStore.backupOverMobileData.first()
                val networkType = if (useMobileData) NetworkType.NOT_ROAMING else NetworkType.UNMETERED

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val periodicWork = PeriodicWorkRequestBuilder<BackupAndUploadWorker>(4, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                        "periodic_upload",
                        ExistingPeriodicWorkPolicy.KEEP,
                        periodicWork
                    )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
