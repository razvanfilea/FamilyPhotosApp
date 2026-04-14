package net.theluckycoder.familyphotos.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.workers.enqueuePeriodBackupWorker
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
                WorkManager.getInstance(context).enqueuePeriodBackupWorker(useMobileData)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
