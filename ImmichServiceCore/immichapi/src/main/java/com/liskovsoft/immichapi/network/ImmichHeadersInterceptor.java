package com.liskovsoft.immichapi.network;

import com.liskovsoft.immichapi.prefs.ImmichPrefs;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Attaches {@code x-api-key} and Accept headers to Immich API requests.
 */
public final class ImmichHeadersInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder builder = original.newBuilder()
                .header(ImmichHeaders.ACCEPT, ImmichHeaders.ACCEPT_JSON);

        if (original.header(ImmichHeaders.API_KEY) == null) {
            String apiKey = resolveApiKey();
            if (apiKey != null) {
                builder.header(ImmichHeaders.API_KEY, apiKey);
            }
        }

        return chain.proceed(builder.build());
    }

    private static String resolveApiKey() {
        try {
            return ImmichPrefs.instance().getApiKey();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
