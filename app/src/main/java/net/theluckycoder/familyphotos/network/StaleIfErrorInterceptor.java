package net.theluckycoder.familyphotos.network;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.CacheControl.Builder;

public class StaleIfErrorInterceptor implements Interceptor {
    // based on code from https://github.com/square/okhttp/issues/1083 by jvincek
    private static final int DEFULAT_MAX_STALE_DURATION = 28;
    private static final TimeUnit DEFULAT_MAX_STALE_TIMEUNIT = TimeUnit.DAYS;

    private final int staleDuration;
    private final TimeUnit staleDurationTimeUnit;

    public StaleIfErrorInterceptor(int staleDuration, TimeUnit staleDurationTimeUnit) {
        // Preconditions check
        if (staleDuration <= 0) throw new AssertionError();
        if (staleDurationTimeUnit == null) throw new AssertionError();

        this.staleDuration = staleDuration;
        this.staleDurationTimeUnit = staleDurationTimeUnit;
    }

    public StaleIfErrorInterceptor() {
        this(DEFULAT_MAX_STALE_DURATION, DEFULAT_MAX_STALE_TIMEUNIT);
    }

    private int getMaxStaleDuration() {
        return staleDuration != -1 ? staleDuration : DEFULAT_MAX_STALE_DURATION;
    }

    private TimeUnit getMaxStaleDurationTimeUnit() {
        return staleDurationTimeUnit != null ? staleDurationTimeUnit : DEFULAT_MAX_STALE_TIMEUNIT;
    }

    @NonNull
    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Response response = null;
        Request request = chain.request();

        // first try the regular (network) request, guard with try-catch
        // so we can retry with force-cache below
        try {
            response = chain.proceed(request);

            // return the original response only if it succeeds
            if (response.isSuccessful()) {
                return response;
            }
        } catch (Exception e) {
            // original request error
        }

        if (response == null || !response.isSuccessful()) {
            CacheControl cacheControl = new Builder().onlyIfCached()
                    .maxStale(getMaxStaleDuration(), getMaxStaleDurationTimeUnit()).build();
            Request newRequest = request.newBuilder().cacheControl(cacheControl).build();
            try {
                response = chain.proceed(newRequest);
            } catch (Exception e) { // cache not available
                throw e;
            }
        }
        return response;
    }
}