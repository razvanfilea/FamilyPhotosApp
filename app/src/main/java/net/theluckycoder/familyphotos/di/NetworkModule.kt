package net.theluckycoder.familyphotos.di

import android.content.Context
import android.net.TrafficStats
import android.util.Log
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.data.remote.PhotosService
import net.theluckycoder.familyphotos.data.remote.UserService
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okio.Path.Companion.toOkioPath
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NetworkModule {

    @Provides
    @Named("setCookie")
    fun providesSetCookieInterceptor(
        userDataStore: UserDataStore,
        scope: IOCoroutineScope
    ): Interceptor {
        var sessionCookie: String? = null

        scope.launch {
            userDataStore.sessionCookie.collectLatest {
                ensureActive()
                sessionCookie = it
            }
        }

        return Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().apply {
                    sessionCookie?.let {
                        addHeader("Cookie", it)
                    }
                }.build()
            )
        }
    }

    @Provides
    @Named("receiveCookie")
    fun providesReceiveCookieInterceptor(
        userDataStore: UserDataStore,
        scope: IOCoroutineScope
    ): Interceptor {
        return Interceptor { chain ->
            val originalResponse: Response = chain.proceed(chain.request())

            if (originalResponse.headers("Set-Cookie").isNotEmpty()) {
                val cookies = HashSet<String>()
                for (header in originalResponse.headers("Set-Cookie")) {
                    cookies.add(header)
                }

                Log.i("Cookies", cookies.toString())

                scope.launch {
                    userDataStore.setSessionCookie(cookies.first())
                }
            }

            originalResponse
        }
    }

    @Singleton
    @Provides
    fun providesOkHttpClient(
        @Named("setCookie") setCookieInterceptor: Interceptor,
        @Named("receiveCookie") receiveCookieInterceptor: Interceptor,
    ): OkHttpClient = runBlocking {
        OkHttpClient.Builder()
            .writeTimeout(Duration.ofMinutes(2))
            .addInterceptor(BrotliInterceptor)
            .addInterceptor(setCookieInterceptor)
            .addInterceptor(receiveCookieInterceptor)
//            .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply { level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC })
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Provides
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Singleton
    @Provides
    fun provideLoginService(retrofit: Retrofit): UserService =
        retrofit.create(UserService::class.java)

    @Singleton
    @Provides
    fun providePhotosService(retrofit: Retrofit): PhotosService =
        retrofit.create(PhotosService::class.java)

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

    const val BASE_URL = "https://faarr.go.ro/"
}