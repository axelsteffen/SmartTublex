package de.developerleipzig.immichapi.media;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImmichPlaybackProbeTest {
    @Test
    public void h264OnlyIsTvFriendly() {
        ImmichPlaybackProbe.Result h264 =
                new ImmichPlaybackProbe.Result("video/mp4", ImmichPlaybackProbe.VideoCodecHint.H264);
        assertTrue(h264.isLikelyTvFriendly());
        assertFalse(h264.isLikelyNeedsTranscode());

        ImmichPlaybackProbe.Result hevc =
                new ImmichPlaybackProbe.Result("video/mp4", ImmichPlaybackProbe.VideoCodecHint.HEVC);
        assertFalse(hevc.isLikelyTvFriendly());
        assertTrue(hevc.isLikelyNeedsTranscode());

        ImmichPlaybackProbe.Result av1 =
                new ImmichPlaybackProbe.Result("video/mp4", ImmichPlaybackProbe.VideoCodecHint.AV1);
        assertFalse(av1.isLikelyTvFriendly());
        assertTrue(av1.isLikelyNeedsTranscode());

        ImmichPlaybackProbe.Result unknown =
                new ImmichPlaybackProbe.Result("video/mp4", ImmichPlaybackProbe.VideoCodecHint.UNKNOWN);
        assertFalse(unknown.isLikelyTvFriendly());
        assertFalse(unknown.isLikelyNeedsTranscode());
    }
}
