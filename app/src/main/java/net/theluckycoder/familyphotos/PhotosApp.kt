package net.theluckycoder.familyphotos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.di.DefaultCoroutineScope
import net.theluckycoder.familyphotos.workers.enqueuePeriodBackupWorker
import javax.inject.Inject

@HiltAndroidApp
class PhotosApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var coroutineScope: DefaultCoroutineScope

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

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

        createUploadWorker()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(Dispatchers.Default.asExecutor())
            .build()

    private fun createUploadWorker() = coroutineScope.launch {
        val workManager = WorkManager.getInstance(this@PhotosApp)

        // TODO: Remove after a few releases - cleans up old worker with wrong name
        workManager.cancelUniqueWork("periodic_upload")

        val useMobileData = settingsDataStore.backupOverMobileData.first()
        workManager.enqueuePeriodBackupWorker(useMobileData)
    }
}
