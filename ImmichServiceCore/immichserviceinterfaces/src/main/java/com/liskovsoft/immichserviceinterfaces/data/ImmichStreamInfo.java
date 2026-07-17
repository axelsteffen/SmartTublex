package com.liskovsoft.immichserviceinterfaces.data;

/**
 * Playable stream metadata for ExoPlayer (direct URL).
 * Auth must be supplied as {@code x-api-key} request header — see {@link #getApiKey()}.
 */
public interface ImmichStreamInfo {
    String getUrl();

    /** Container/MIME hint, e.g. video/mp4. */
    String getContainer();

    /** API key to send as {@code x-api-key} with media requests (may be null). */
    String getApiKey();
}
