package net.theluckycoder.familyphotos.core.data.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.theluckycoder.familyphotos.core.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.core.data.remote.FolderService
import net.theluckycoder.familyphotos.core.data.remote.PhotosService
import net.theluckycoder.familyphotos.core.data.remote.SharingService
import net.theluckycoder.familyphotos.core.data.remote.SyncService
import net.theluckycoder.familyphotos.core.data.remote.UserService
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
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
    internal fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Singleton
    @Provides
    internal fun provideLoginService(retrofit: Retrofit): UserService =
        retrofit.create(UserService::class.java)

    @Singleton
    @Provides
    internal fun providePhotosService(retrofit: Retrofit): PhotosService =
        retrofit.create(PhotosService::class.java)

    @Singleton
    @Provides
    internal fun provideSyncService(retrofit: Retrofit): SyncService =
        retrofit.create(SyncService::class.java)

    @Singleton
    @Provides
    internal fun provideSharingService(retrofit: Retrofit): SharingService =
        retrofit.create(SharingService::class.java)

    @Singleton
    @Provides
    internal fun provideFolderService(retrofit: Retrofit): FolderService =
        retrofit.create(FolderService::class.java)

    const val BASE_URL = "https://faarr.go.ro/"
}