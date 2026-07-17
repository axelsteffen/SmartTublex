package de.developerleipzig.immichapi.network;

import de.developerleipzig.immichapi.prefs.ImmichPrefs;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit factory for Immich server API calls.
 */
public final class ImmichRetrofitHelper {
    private static OkHttpClient sClient;
    private static String sOverrideApiBaseUrl;

    private ImmichRetrofitHelper() {
    }

    /** Override API base URL (MockWebServer). Resets cached HTTP client. */
    public static synchronized void setApiBaseUrlOverride(String apiBaseUrl) {
        sOverrideApiBaseUrl = apiBaseUrl;
        sClient = null;
    }

    public static synchronized void reset() {
        sOverrideApiBaseUrl = null;
        sClient = null;
    }

    public static <T> T createApi(Class<T> clazz) {
        return createApi(resolveApiBaseUrl(), clazz);
    }

    public static <T> T createApi(String apiBaseUrl, Class<T> clazz) {
        return buildRetrofit(ImmichUrlHelper.normalizeApiBaseUrl(apiBaseUrl)).create(clazz);
    }

    private static String resolveApiBaseUrl() {
        if (sOverrideApiBaseUrl != null && !sOverrideApiBaseUrl.isEmpty()) {
            return sOverrideApiBaseUrl;
        }
        String serverUrl = ImmichPrefs.instance().getServerUrl();
        if (serverUrl == null || serverUrl.isEmpty()) {
            throw new IllegalStateException("Immich server URL not configured");
        }
        return ImmichUrlHelper.normalizeApiBaseUrl(serverUrl);
    }

    private static Retrofit buildRetrofit(String baseUrl) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(getClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    private static synchronized OkHttpClient getClient() {
        if (sClient == null) {
            sClient = new OkHttpClient.Builder()
                    .addInterceptor(new ImmichHeadersInterceptor())
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build();
        }
        return sClient;
    }
}
