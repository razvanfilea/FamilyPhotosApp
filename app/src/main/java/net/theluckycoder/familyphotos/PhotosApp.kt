package net.theluckycoder.familyphotos

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto
import net.theluckycoder.familyphotos.di.DefaultCoroutineScope
import net.theluckycoder.familyphotos.workers.BackupWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class PhotosApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var coroutineScope: DefaultCoroutineScope

    @Inject
    lateinit var networkPhotosDao: NetworkPhotosDao

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

        if (BuildConfig.BENCHMARK) {
            seedBenchmarkData()
        }
        createUploadWorker()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setExecutor(Dispatchers.Default.asExecutor())
            .build()

    private fun createUploadWorker() = coroutineScope.launch {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicUpload =
            PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

        try {
            WorkManager.getInstance(this@PhotosApp)
                .enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_UPLOAD,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicUpload
                )
                .await()
            Log.i(BackupWorker::class.simpleName, "Backup has been enabled")
        } catch (e: Throwable) {
            Log.e(BackupWorker::class.simpleName, "Backup failed to be enabled", e)
        }
    }

    private fun seedBenchmarkData() = coroutineScope.launch {
        val baseTime = 1704067200L // 2024-01-01 00:00:00 UTC
        val photos = List(10_000) { i ->
            NetworkPhoto(
                id = i.toLong() + 1,
                userId = "bench",
                name = "bench_$i.jpg",
                timeCreated = baseTime - i * 21600L, // 6 hours apart
                fileSize = 1024L,
            )
        }
        networkPhotosDao.insert(photos)
    }

    companion object {
        private const val UNIQUE_PERIODIC_UPLOAD = "periodic_upload"
    }
}
