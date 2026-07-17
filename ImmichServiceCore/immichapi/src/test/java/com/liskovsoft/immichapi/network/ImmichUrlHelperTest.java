package com.liskovsoft.immichapi.network;

import org.junit.Assert;
import org.junit.Test;

public class ImmichUrlHelperTest {
    @Test
    public void normalizeApiBaseUrl_appendsApi() {
        Assert.assertEquals("https://immich.example/api/",
                ImmichUrlHelper.normalizeApiBaseUrl("https://immich.example"));
        Assert.assertEquals("https://immich.example/api/",
                ImmichUrlHelper.normalizeApiBaseUrl("https://immich.example/"));
        Assert.assertEquals("https://immich.example/api/",
                ImmichUrlHelper.normalizeApiBaseUrl("https://immich.example/api"));
        Assert.assertEquals("https://immich.example/api/",
                ImmichUrlHelper.normalizeApiBaseUrl("https://immich.example/api/"));
    }

    @Test
    public void normalizeServerUrl_stripsApi() {
        Assert.assertEquals("https://immich.example",
                ImmichUrlHelper.normalizeServerUrl("https://immich.example/api/"));
    }

    @Test
    public void assetUrls() {
        String base = "https://immich.example/api/";
        Assert.assertEquals(
                "https://immich.example/api/assets/abc/thumbnail",
                ImmichUrlHelper.assetThumbnailUrl(base, "abc"));
        Assert.assertEquals(
                "https://immich.example/api/assets/abc/video/playback",
                ImmichUrlHelper.assetVideoPlaybackUrl(base, "abc"));
        Assert.assertEquals(
                "https://immich.example/api/assets/abc/original",
                ImmichUrlHelper.assetOriginalUrl(base, "abc"));
    }
}
