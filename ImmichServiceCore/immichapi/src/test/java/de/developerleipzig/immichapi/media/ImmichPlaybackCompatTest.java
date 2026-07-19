package de.developerleipzig.immichapi.media;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImmichPlaybackCompatTest {
    @Test
    public void allowsOnlyWhenH264Confirmed() {
        assertTrue(ImmichPlaybackCompat.isLikelyPlayableOnTv(true, "video/mp4"));
        assertFalse(ImmichPlaybackCompat.isLikelyPlayableOnTv(false, "video/hevc"));
        assertFalse(ImmichPlaybackCompat.isLikelyPlayableOnTv(false, "video/webm"));
    }

    @Test
    public void unsupportedMessageMentionsH264() {
        String msg = ImmichPlaybackCompat.unsupportedMessage("video/hevc");
        assertTrue(msg.contains("hevc"));
        assertTrue(msg.contains("H.264"));
    }
}
