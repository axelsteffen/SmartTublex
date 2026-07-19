package de.developerleipzig.immichapi.media;

import androidx.annotation.Nullable;

/**
 * Immich TV Direct Play gate. Refuses only when the stream flag says Direct Play is unsafe
 * (positive HEVC/AV1/VP9 sniff). H.264 and UNKNOWN are allowed.
 */
public final class ImmichPlaybackCompat {
    private ImmichPlaybackCompat() {
    }

    /**
     * @param hasEncodedVideo {@link de.developerleipzig.immichserviceinterfaces.data.ImmichStreamInfo#hasEncodedVideo()}
     * @param container       informational (MIME / codec hint)
     */
    public static boolean isLikelyPlayableOnTv(boolean hasEncodedVideo, @Nullable String container) {
        return hasEncodedVideo;
    }

    public static String unsupportedMessage(@Nullable String container) {
        String mime = (container != null && !container.isEmpty()) ? container : "unknown";
        return "Immich: " + mime
                + " — TV needs H.264. In Immich accept only H.264 and re-run video transcoding.";
    }
}
