package net.theluckycoder.familyphotos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.di.DefaultCoroutineScope
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.core.data.local.datastore.SharedActivityStore
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
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(Dispatchers.Default.asExecutor())
            .build()

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
