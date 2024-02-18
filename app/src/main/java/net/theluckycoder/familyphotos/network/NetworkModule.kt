package net.theluckycoder.familyphotos.network

import android.content.Context
import android.util.Log
import coil.ComponentRegistry
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.nycode.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
import retrofit2.Retrofit
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
            .components(fun ComponentRegistry.Builder.() {
                add(ImageDecoderDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            })
            .okHttpClient(okHttpClient)
            // Cache
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.35).build() }
            .diskCache {
                runBlocking {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache"))
                        .minimumMaxSizeBytes(1024L * 1024L * 1024L * 512L) // 512MB
                        .maximumMaxSizeBytes(settingsDataStore.cacheSizeMbFlow.first() * 1024L)
                        .build()
                }
            }
            .respectCacheHeaders(false) // Cache-First
            .build()

    const val BASE_URL = "https://faarr.go.ro/"
}
