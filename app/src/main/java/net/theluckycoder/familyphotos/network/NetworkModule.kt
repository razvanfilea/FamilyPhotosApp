package net.theluckycoder.familyphotos.network

import android.content.Context
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.fetch.VideoFrameFileFetcher
import coil.fetch.VideoFrameUriFetcher
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.theluckycoder.familyphotos.BuildConfig
import net.theluckycoder.familyphotos.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.datastore.UserDataStore
import net.theluckycoder.familyphotos.network.service.PhotosService
import net.theluckycoder.familyphotos.network.service.UserService
import net.theluckycoder.familyphotos.utils.IOCoroutineScope
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NetworkModule {

    @Provides
    @Named("auth")
    fun providesAuthInterceptor(
        userDataStore: UserDataStore,
        scope: IOCoroutineScope
    ): Interceptor {
        var authenticationCredentials: String? = null

        scope.launch {
            userDataStore.credentials.collectLatest {
                ensureActive()
                authenticationCredentials = it
            }
        }

        return Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().apply {
                    authenticationCredentials?.let {
                        addHeader("Authorization", "Basic $authenticationCredentials")
                    }
                }.build()
            )
        }
    }

    /**
     * Cloud Response Header Interceptor for Configuring Caching Policies
     * Dangerous interceptor that rewrites the server's cache-control header.
     */
    @Provides
    @Named("cacheControl")
    fun providesCacheControlInterceptor(): Interceptor = CacheFirstInterceptor()

    @Singleton
    @Provides
    fun providesOkHttpClient(
        @ApplicationContext context: Context,
        @Named("auth") authInterceptor: Interceptor,
        @Named("cacheControl") cacheControlInterceptor: Interceptor,
        settingsDataStore: SettingsDataStore,
    ): OkHttpClient = runBlocking {
        val cache = Cache(
            File(context.cacheDir, "okhttp"),
            settingsDataStore.cacheSizeMbFlow.first() * (1024L * 1024L)
        )

        val certificates = SslUtils.getCertificates()

        OkHttpClient.Builder()
            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(authInterceptor)
            .addInterceptor(cacheControlInterceptor)
            .cache(cache)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Singleton
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
        okHttpClient: OkHttpClient
    ): ImageLoader = ImageLoader.Builder(context)
        .availableMemoryPercentage(0.3)
        .bitmapPoolPercentage(0.0)
        .logger(DebugLogger().takeIf { BuildConfig.DEBUG })
        .componentRegistry {
            add(ImageDecoderDecoder(context))
            add(VideoFrameFileFetcher(context))
            add(VideoFrameUriFetcher(context))
            add(VideoFrameDecoder(context))
        }
        .okHttpClient(okHttpClient)
        .build()

    const val BASE_URL = "https://razvanrares.go.ro:9002/"
}
