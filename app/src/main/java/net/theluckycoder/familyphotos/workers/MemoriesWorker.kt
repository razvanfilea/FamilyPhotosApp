package net.theluckycoder.familyphotos.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.core.data.model.isVideo
import net.theluckycoder.familyphotos.notifications.MemoriesNotifier

/**
 * Re-enqueues itself for the next 09:00 instead of being periodic: a periodic job's period is
 * measured from its last actual run, so delayed runs would push the time later and later.
 */
@HiltWorker
class MemoriesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val photosRepository: PhotosRepository,
    private val settingsDataStore: SettingsDataStore,
    private val notifier: MemoriesNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settingsDataStore.notifyMemories.first()) return Result.success()

        try {
            notifyIfAny()
        } finally {
            // Stopped means cancelled, or interrupted and retried by WorkManager
            if (!isStopped) {
                WorkManager.getInstance(applicationContext)
                    .enqueueMemoriesWorker(ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
        }
        return Result.success()
    }

    private suspend fun notifyIfAny() {
        if (!notifier.canPost) return

        val today = LocalDate.now().toEpochDay()
        if (settingsDataStore.getLastMemoriesDay() == today) return

        val memories = photosRepository.getMemoriesNow(settingsDataStore.currentPhotoType())

        // The query window is a few days wide, so most photos show up on several consecutive days
        val notified = settingsDataStore.getNotifiedMemoryIds()
        val memory = memories.firstOrNull { memory -> memory.allPhotos.any { it.id !in notified } }
            ?: return
        val fresh = memory.allPhotos.filter { it.id !in notified }
        // Highlights are ranked: the first fresh photo of them is the best one left
        val cover = memory.cover.takeIf { it in fresh }
            ?: memory.highlights.firstOrNull { it in fresh && !it.isVideo }
            ?: fresh.firstOrNull { !it.isVideo }
            ?: fresh.first()

        notifier.post(memory.yearsAgo, fresh.size, cover = cover)
        settingsDataStore.setLastMemoriesDay(today)

        val inWindow = memories.flatMap { it.allPhotos }.map { it.id }.toSet()
        settingsDataStore.setNotifiedMemoryIds((notified + fresh.map { it.id }) intersect inWindow)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "memories_work"
        const val NOTIFY_HOUR = 9
    }
}
