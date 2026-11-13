package net.theluckycoder.familyphotos.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.widget.MemoriesWidget
import net.theluckycoder.familyphotos.workers.enqueueMemoriesWidgetWorker
import net.theluckycoder.familyphotos.workers.enqueueMemoriesWorker
import javax.inject.Inject

/**
 * The memories delays were computed for the old clock or time zone, so 09:00 and the widget's
 * slots have moved.
 */
@AndroidEntryPoint
class TimeChangedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsStore: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED && intent.action != Intent.ACTION_TIME_CHANGED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val workManager = WorkManager.getInstance(context)
                if (settingsStore.notifyMemories.first()) {
                    workManager.enqueueMemoriesWorker(ExistingWorkPolicy.REPLACE)
                }
                if (MemoriesWidget.hasAny(context)) {
                    // Now, not at the next slot: a new day may have started. Replacing the worker
                    // also moves its next slots to the new clock.
                    workManager.enqueueMemoriesWidgetWorker(ExistingWorkPolicy.REPLACE, immediately = true)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
