package net.theluckycoder.familyphotos.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.theluckycoder.familyphotos.widget.MemoriesWidget

/**
 * Shows the next photo in the memories widgets, and today's memories after midnight. Re-enqueues
 * itself at the start of the next slot, like [MemoriesWorker].
 */
class MemoriesWidgetWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // The last widget was removed while this was waiting
        if (!MemoriesWidget.hasAny(applicationContext)) return Result.success()

        try {
            MemoriesWidget.updateAllTypes(applicationContext)
        } finally {
            if (!isStopped) {
                WorkManager.getInstance(applicationContext)
                    .enqueueMemoriesWidgetWorker(ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "memories_widget_work"
    }
}
