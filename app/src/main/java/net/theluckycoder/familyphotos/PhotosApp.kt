package net.theluckycoder.familyphotos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.di.DefaultCoroutineScope
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.core.data.local.datastore.SharedActivityStore
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.widget.MemoriesWidget
import net.theluckycoder.familyphotos.workers.enqueueMemoriesWidgetWorker
import net.theluckycoder.familyphotos.workers.enqueuePeriodBackupWorker
import net.theluckycoder.familyphotos.workers.enqueueMemoriesWorker
import net.theluckycoder.familyphotos.workers.enqueueSharedActivityWorker
import net.theluckycoder.familyphotos.notifications.AppForegroundTracker
import net.theluckycoder.familyphotos.notifications.MemoriesNotifier
import net.theluckycoder.familyphotos.notifications.SharedFolderNotifier
import javax.inject.Inject

@HiltAndroidApp
class PhotosApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var coroutineScope: DefaultCoroutineScope

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var sharedActivityStore: SharedActivityStore

    @Inject
    lateinit var userDataStore: UserDataStore

    @Inject
    lateinit var photosRepository: PhotosRepository

    @Inject
    lateinit var sharedFolderNotifier: SharedFolderNotifier

    @Inject
    lateinit var memoriesNotifier: MemoriesNotifier

    override fun onCreate() {
        /*if (BuildConfig.DEBUG) {
           android.os.StrictMode.setThreadPolicy(
               android.os.StrictMode.ThreadPolicy.Builder()
                   .detectAll()
                   .penaltyLog()
                   .build()
           )
           android.os.StrictMode.setVmPolicy(
               android.os.StrictMode.VmPolicy.Builder()
                   .detectAll()
                   .build()
           )
       }*/
        super.onCreate()

        // Created up front so they can be configured in the system settings before the first post
        sharedFolderNotifier.createChannel()
        memoriesNotifier.createChannel()

        // Changes found while the app was open were seen by the user, don't notify about them later
        AppForegroundTracker.register(this) {
            coroutineScope.launch {
                sharedActivityStore.update { it.dropUnseen() }
            }
        }

        createUploadWorker()
        updateMemoriesWidget()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(Dispatchers.Default.asExecutor())
            .build()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun updateMemoriesWidget() = coroutineScope.launch {
        if (MemoriesWidget.hasAny(this@PhotosApp)) {
            WorkManager.getInstance(this@PhotosApp).enqueueMemoriesWidgetWorker()
        }

        // Redraw when the memories change: signing in or out, a sync bringing photos (right after
        // signing in the database is still empty), a new day. The user flow starts with a
        // placeholder: skip it until the stored user shows up. The first memories are what the
        // widgets already show.
        val stored = userDataStore.getUser()?.userId
        userDataStore.user.map { it?.userId }
            .dropWhile { it != stored }
            .distinctUntilChanged()
            .flatMapLatest {
                combine(
                    photosRepository.getMemories(PhotoType.Personal),
                    photosRepository.getMemories(PhotoType.Family),
                ) { personal, family -> personal to family }
            }
            .distinctUntilChanged()
            .drop(1)
            .collect { MemoriesWidget.updateAllTypes(this@PhotosApp) }
    }

    private fun createUploadWorker() = coroutineScope.launch {
        val workManager = WorkManager.getInstance(this@PhotosApp)

        val useMobileData = settingsDataStore.backupOverMobileData.first()
        workManager.enqueuePeriodBackupWorker(useMobileData)

        if (settingsDataStore.notifySharedFolders.first()) {
            // UPDATE so an install that still has an older period moves to the current one
            workManager.enqueueSharedActivityWorker(ExistingPeriodicWorkPolicy.UPDATE)
        }
        if (settingsDataStore.notifyMemories.first()) {
            workManager.enqueueMemoriesWorker()
        }
    }
}
