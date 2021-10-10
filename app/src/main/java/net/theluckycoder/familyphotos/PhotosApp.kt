package net.theluckycoder.familyphotos

import android.app.Application
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.security.ProviderInstaller
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.theluckycoder.familyphotos.datastore.UserDataStore
import javax.inject.Inject

@HiltAndroidApp
class PhotosApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var userDataStore: UserDataStore

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork() // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build()
            )
            /*StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )*/
        }
        super.onCreate()

        GlobalScope.launch(Dispatchers.IO) {
            if (userDataStore.firstStart.first()) {
                installServiceProviderIfNeeded()
                userDataStore.setFirstStart()
            }
        }
    }

    override fun getWorkManagerConfiguration() =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(Dispatchers.Default.asExecutor())
            .build()

    @SuppressWarnings("deprecation")
    private fun installServiceProviderIfNeeded() {
        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (e: GooglePlayServicesRepairableException) {
            e.printStackTrace()

            // Prompt the user to install/update/enable Google Play services.
            GooglePlayServicesUtil.showErrorNotification(e.connectionStatusCode, this)
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }
    }
}
