package net.theluckycoder.familyphotos.di

import android.content.Context
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.video.VideoFrameDecoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.theluckycoder.familyphotos.core.data.local.datastore.SettingsDataStore
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AppModule {

    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Singleton
    @Provides
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        settingsDataStore: SettingsDataStore
    ): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(AnimatedImageDecoder.Factory())
                add(VideoFrameDecoder.Factory())
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            okHttpClient
                        }
                    )
                )
            }
            // Limit parallelism to reduce DiskLruCache lock contention during fast scrolling
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism((Runtime.getRuntime().availableProcessors() * 1.5).toInt()))
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.40).build() }
            .diskCache {
                runBlocking {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                        .minimumMaxSizeBytes(512L * 1024L * 1024L) // 512MB
                        .maximumMaxSizeBytes(settingsDataStore.cacheSizeMbFlow.first() * 1024L)
                        .build()
                }
            }
            .build()
}