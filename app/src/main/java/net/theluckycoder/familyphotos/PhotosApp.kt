package net.theluckycoder.familyphotos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.datetime.TimeZone
import net.theluckycoder.familyphotos.datastore.UserDataStore
import javax.inject.Inject

@HiltAndroidApp
class PhotosApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var userDataStore: UserDataStore

    override fun onCreate() {
        /*if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork() // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build()
            )
        }*/
        super.onCreate()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(Dispatchers.Default.asExecutor())
            .build()

    companion object {
        val LOCAL_TIME_ZONE = TimeZone.currentSystemDefault()
    }
}
