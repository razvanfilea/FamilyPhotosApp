package net.theluckycoder.familyphotos.network

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import coil3.video.VideoFrameDecoder
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
import net.theluckycoder.familyphotos.BuildConfig
import net.theluckycoder.familyphotos.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.datastore.UserDataStore
import net.theluckycoder.familyphotos.network.service.PhotosService
import net.theluckycoder.familyphotos.network.service.UserService
import net.theluckycoder.familyphotos.utils.IOCoroutineScope
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
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
            .addInterceptor(HttpLoggingInterceptor())
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
            .logger(DebugLogger().takeIf { BuildConfig.DEBUG })
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
            // Cache
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.35).build() }
            .diskCache {
                runBlocking {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                        .minimumMaxSizeBytes(1024L * 1024L * 1024L * 512L) // 512MB
                        .maximumMaxSizeBytes(settingsDataStore.cacheSizeMbFlow.first() * 1024L)
                        .build()
                }
            }
//            .respectCacheHeaders(false) // Cache-First
            .build()

    const val BASE_URL = "https://faarr.go.ro/"
}
