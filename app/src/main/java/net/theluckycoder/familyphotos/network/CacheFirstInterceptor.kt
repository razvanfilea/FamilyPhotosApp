package net.theluckycoder.familyphotos.network

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.util.concurrent.TimeUnit

class CacheFirstInterceptor @JvmOverloads constructor(
    private val staleDuration: Int = DEFAULT_MAX_STALE_DURATION,
    private val staleDurationTimeUnit: TimeUnit = DEFAULT_MAX_STALE_TIMEUNIT
) : Interceptor {

    init {
        require(staleDuration > 0)
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()

        if (originalRequest.method == "GET") {
            // first try the regular (network) request, guard with try-catch
            // so we can retry with force-cache below
            val cacheControl = CacheControl.Builder().onlyIfCached()
                .maxStale(staleDuration, staleDurationTimeUnit).build()
            val newRequest = originalRequest.newBuilder().cacheControl(cacheControl).build()
            try {
                val response = chain.proceed(newRequest)

                // return the original response only if it succeeds
                if (response.isSuccessful)
                    return response
                else
                    response.closeQuietly()
            } catch (e: Exception) {
                // original request error
            }
        }

        try {
            return chain.proceed(originalRequest)
        } catch (e: Exception) { // cache not available
            throw e
        }
    }

    companion object {
        // based on code from https://github.com/square/okhttp/issues/1083 by jvincek
        private const val DEFAULT_MAX_STALE_DURATION = 28
        private val DEFAULT_MAX_STALE_TIMEUNIT = TimeUnit.DAYS
    }
}
