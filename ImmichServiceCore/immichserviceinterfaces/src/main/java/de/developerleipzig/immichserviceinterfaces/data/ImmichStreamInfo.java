package de.developerleipzig.immichserviceinterfaces.data;

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

    /**
     * True when TV Direct Play should proceed. False only when the probe positively
     * found HEVC/AV1/VP9 (legacy TVs need an Immich H.264 encode). UNKNOWN / probe
     * failure fails open so H.264 with late {@code moov} is not blocked.
     */
    boolean hasEncodedVideo();
}
