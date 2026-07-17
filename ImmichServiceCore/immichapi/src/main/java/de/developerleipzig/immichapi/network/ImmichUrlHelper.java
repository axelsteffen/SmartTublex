package de.developerleipzig.immichapi.network;

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
        if (assetId == null || assetId.isEmpty()) {
            return null;
        }
        return normalizeApiBaseUrl(apiBaseUrl) + "assets/" + assetId + "/thumbnail";
    }

    public static String assetVideoPlaybackUrl(String apiBaseUrl, String assetId) {
        if (assetId == null || assetId.isEmpty()) {
            return null;
        }
        return normalizeApiBaseUrl(apiBaseUrl) + "assets/" + assetId + "/video/playback";
    }

    public static String assetOriginalUrl(String apiBaseUrl, String assetId) {
        if (assetId == null || assetId.isEmpty()) {
            return null;
        }
        return normalizeApiBaseUrl(apiBaseUrl) + "assets/" + assetId + "/original";
    }
}
