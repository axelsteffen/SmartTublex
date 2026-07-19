package de.developerleipzig.immichapi.media;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Probes Immich {@code /video/playback}: Content-Type plus a cheap MP4 codec sniff.
 * <p>
 * Immich may accept H.264+HEVC, but many Android TVs (e.g. 2015 Bravia) only Direct-Play
 * H.264 reliably. HEVC/AV1/VP9 are treated as needing an H.264 encode from Immich.
 */
public final class ImmichPlaybackProbe {
    /** Enough for moov-at-start Immich encodes; keeps TV probe light. */
    private static final long PROBE_BYTES = 256 * 1024L - 1;

    public enum VideoCodecHint {
        H264,
        HEVC,
        AV1,
        VP9,
        UNKNOWN
    }

    public static final class Result {
        @Nullable
        public final String contentType;
        public final VideoCodecHint codecHint;

        Result(@Nullable String contentType, VideoCodecHint codecHint) {
            this.contentType = contentType;
            this.codecHint = codecHint != null ? codecHint : VideoCodecHint.UNKNOWN;
        }

        /**
         * Confirmed H.264 in the probe window. Prefer this when present; UNKNOWN is still
         * allowed for Direct Play (see {@link #isLikelyNeedsTranscode()}).
         */
        public boolean isLikelyTvFriendly() {
            return codecHint == VideoCodecHint.H264;
        }

        /**
         * Positive HEVC/AV1/VP9 sniff — refuse Direct Play on legacy TVs.
         * UNKNOWN must not count as needing transcode (moov often outside the probe window).
         */
        public boolean isLikelyNeedsTranscode() {
            return codecHint == VideoCodecHint.HEVC
                    || codecHint == VideoCodecHint.AV1
                    || codecHint == VideoCodecHint.VP9;
        }
    }

    private ImmichPlaybackProbe() {
    }

    @Nullable
    public static String probeContentType(OkHttpClient client, String playbackUrl) {
        Result result = probe(client, playbackUrl);
        return result != null ? result.contentType : null;
    }

    @Nullable
    public static Result probe(OkHttpClient client, String playbackUrl) {
        if (client == null || playbackUrl == null || playbackUrl.isEmpty()) {
            return null;
        }
        Request request = new Request.Builder()
                .url(playbackUrl)
                .header("Range", "bytes=0-" + PROBE_BYTES)
                .header("Accept", "*/*")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 206) {
                return null;
            }
            String type = response.header("Content-Type");
            if (type != null) {
                int semi = type.indexOf(';');
                type = semi >= 0 ? type.substring(0, semi).trim() : type.trim();
            }
            VideoCodecHint hint = VideoCodecHint.UNKNOWN;
            ResponseBody body = response.body();
            if (body != null) {
                hint = sniffCodec(body.bytes());
            }
            return new Result(type, hint);
        } catch (IOException e) {
            return null;
        }
    }

    static VideoCodecHint sniffCodec(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return VideoCodecHint.UNKNOWN;
        }
        // FourCCs appear as ASCII in MP4 sample-description boxes.
        String ascii = new String(data, StandardCharsets.ISO_8859_1).toLowerCase(Locale.US);
        if (ascii.contains("hev1") || ascii.contains("hvc1") || ascii.contains("hevc")) {
            return VideoCodecHint.HEVC;
        }
        if (ascii.contains("av01") || ascii.contains("av1c")) {
            return VideoCodecHint.AV1;
        }
        if (ascii.contains("vp09") || ascii.contains("vp9")) {
            return VideoCodecHint.VP9;
        }
        if (ascii.contains("avc1") || ascii.contains("avc3") || ascii.contains("avcc")) {
            return VideoCodecHint.H264;
        }
        return VideoCodecHint.UNKNOWN;
    }
}
