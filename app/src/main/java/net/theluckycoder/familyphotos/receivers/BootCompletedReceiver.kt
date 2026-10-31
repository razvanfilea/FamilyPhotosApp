package net.theluckycoder.familyphotos.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.workers.enqueuePeriodBackupWorker
import net.theluckycoder.familyphotos.workers.enqueueMemoriesWorker
import net.theluckycoder.familyphotos.workers.enqueueSharedActivityWorker
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsStore: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val useMobileData = settingsStore.backupOverMobileData.first()
                val workManager = WorkManager.getInstance(context)
                workManager.enqueuePeriodBackupWorker(useMobileData)
                if (settingsStore.notifySharedFolders.first()) {
                    workManager.enqueueSharedActivityWorker(ExistingPeriodicWorkPolicy.UPDATE)
                }
                if (settingsStore.notifyMemories.first()) {
                    workManager.enqueueMemoriesWorker()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
