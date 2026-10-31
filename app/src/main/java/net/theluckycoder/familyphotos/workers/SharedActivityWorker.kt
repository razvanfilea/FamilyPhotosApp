package net.theluckycoder.familyphotos.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.core.data.local.datastore.SharedActivityStore
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.domain.RefreshPhotosUseCase
import net.theluckycoder.familyphotos.notifications.AppForegroundTracker
import net.theluckycoder.familyphotos.notifications.SharedFolderNotifier

@HiltWorker
class SharedActivityWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    private val sharedActivityStore: SharedActivityStore,
    private val settingsDataStore: SettingsDataStore,
    private val userDataStore: UserDataStore,
    private val foldersRepository: FoldersRepository,
    private val notifier: SharedFolderNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settingsDataStore.notifySharedFolders.first()) return Result.success()

        // Signed out (also after a session expiry): nothing to sync, and old notifications are stale
        if (userDataStore.getUser() == null) {
            notifier.cancelAll()
            return Result.success()
        }

        val refreshResult = try {
            refreshPhotosUseCase(refreshDeviceAlbums = false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh photos", e)
            return Result.retry()
        }
        if (refreshResult is RefreshPhotosUseCase.Result.NotLoggedIn) {
            notifier.cancelAll()
            return Result.success()
        }

        removeUnsharedFolders()

        val dirty = sharedActivityStore.read().folders.filter { it.dirty }
        if (dirty.isEmpty()) return Result.success()

        // In the foreground the in-app refresh already showed the change. Without the permission
        // the change must not pop up later, whenever it gets granted.
        if (AppForegroundTracker.isInForeground || !notifier.canPost) {
            sharedActivityStore.update { it.dropUnseen() }
            return Result.success()
        }

        val memberNames = sharedActivityStore.getMemberNames()
        for (queued in dirty) {
            // Re-read: the user may have opened the folder, or a sync may have added to it, meanwhile
            val activity = sharedActivityStore.read().folders
                .firstOrNull { it.folderId == queued.folderId && it.dirty } ?: continue
            val folderName = foldersRepository.getFolderFlow(activity.folderId).first()?.name ?: continue

            notifier.post(activity, folderName, memberNames)

            // Only if nothing changed since, or the newer additions would never be announced
            sharedActivityStore.update { store ->
                store.copy(
                    folders = store.folders.map {
                        if (it == activity) it.copy(dirty = false, notified = true) else it
                    }
                )
            }
        }

        return Result.success()
    }

    /** A folder missing from the DB was unshared: its notification would open an empty folder. */
    private suspend fun removeUnsharedFolders() {
        val unshared = sharedActivityStore.read().folders
            .map { it.folderId }
            .filter { foldersRepository.getFolderFlow(it).first() == null }
        if (unshared.isEmpty()) return

        unshared.forEach { notifier.cancel(it) }
        sharedActivityStore.update { store ->
            store.copy(folders = store.folders.filterNot { it.folderId in unshared })
        }
    }

    companion object {
        private const val TAG = "SharedActivityWorker"
        const val UNIQUE_WORK_NAME = "shared_activity_work"
    }
}
