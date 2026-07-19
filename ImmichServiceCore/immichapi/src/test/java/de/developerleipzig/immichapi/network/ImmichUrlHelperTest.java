package de.developerleipzig.immichapi.network;

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

    @Test
    public void assetUrls_appendApiKeyQuery() {
        String base = "https://immich.example/api/";
        Assert.assertEquals(
                "https://immich.example/api/assets/abc/thumbnail?apiKey=secret",
                ImmichUrlHelper.assetThumbnailUrl(base, "abc", "secret"));
        Assert.assertEquals(
                "https://immich.example/api/assets/abc/video/playback?apiKey=secret",
                ImmichUrlHelper.assetVideoPlaybackUrl(base, "abc", "secret"));
        Assert.assertEquals(
                "https://immich.example/api/assets/abc/thumbnail?apiKey=secret",
                ImmichUrlHelper.withApiKey(
                        "https://immich.example/api/assets/abc/thumbnail?apiKey=secret", "other"));
    }
}
