package de.developerleipzig.immichapi.network;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds absolute Immich API URLs from a configured server base.
 */
public final class ImmichUrlHelper {
    private ImmichUrlHelper() {
    }

    /**
     * Normalizes a user-entered server URL to an Immich API base ending with {@code /api/}.
     * <p>
     * Accepts {@code https://host}, {@code https://host/}, or {@code https://host/api}.
     */
    public static String normalizeApiBaseUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("serverUrl required");
        }
        String url = serverUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/api")) {
            return url + "/";
        }
        return url + "/api/";
    }

    /** Server origin without {@code /api/} (for display / prefs). */
    public static String normalizeServerUrl(String serverUrl) {
        String api = normalizeApiBaseUrl(serverUrl);
        if (api.endsWith("/api/")) {
            return api.substring(0, api.length() - 5);
        }
        return api;
    }

    public static String assetThumbnailUrl(String apiBaseUrl, String assetId) {
        return assetThumbnailUrl(apiBaseUrl, assetId, null);
    }

    public static String assetThumbnailUrl(String apiBaseUrl, String assetId, String apiKey) {
        if (assetId == null || assetId.isEmpty()) {
            return null;
        }
        return withApiKey(normalizeApiBaseUrl(apiBaseUrl) + "assets/" + assetId + "/thumbnail", apiKey);
    }

    public static String assetVideoPlaybackUrl(String apiBaseUrl, String assetId) {
        return assetVideoPlaybackUrl(apiBaseUrl, assetId, null);
    }

    public static String assetVideoPlaybackUrl(String apiBaseUrl, String assetId, String apiKey) {
        if (assetId == null || assetId.isEmpty()) {
            return null;
        }
        return withApiKey(
                normalizeApiBaseUrl(apiBaseUrl) + "assets/" + assetId + "/video/playback", apiKey);
    }

    public static String assetOriginalUrl(String apiBaseUrl, String assetId) {
        return assetOriginalUrl(apiBaseUrl, assetId, null);
    }

    public static String assetOriginalUrl(String apiBaseUrl, String assetId, String apiKey) {
        if (assetId == null || assetId.isEmpty()) {
            return null;
        }
        return withApiKey(normalizeApiBaseUrl(apiBaseUrl) + "assets/" + assetId + "/original", apiKey);
    }

    /**
     * Immich accepts {@code ?apiKey=} as an alternative to the {@code x-api-key} header.
     * Embedding the key lets Glide / ExoPlayer fetch media without a custom OkHttp interceptor.
     */
    public static String withApiKey(String url, String apiKey) {
        if (url == null || url.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            return url;
        }
        if (url.contains("apiKey=")) {
            return url;
        }
        String encoded;
        try {
            encoded = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            encoded = apiKey;
        }
        char sep = url.indexOf('?') >= 0 ? '&' : '?';
        return url + sep + "apiKey=" + encoded;
    }
}
