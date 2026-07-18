package de.developerleipzig.immichapi.service;

import de.developerleipzig.immichapi.adapter.ImmichMediaItemFormatInfo;
import de.developerleipzig.immichapi.library.ImmichAssetImpl;
import de.developerleipzig.immichapi.network.ImmichApi;
import de.developerleipzig.immichapi.network.ImmichHeaders;
import de.developerleipzig.immichapi.network.ImmichRetrofitHelper;
import de.developerleipzig.immichapi.prefs.ImmichPrefs;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset;
import de.developerleipzig.immichserviceinterfaces.data.ImmichStreamInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * MockWebServer coverage for Immich stream URL resolve (Phase 1.5 / 1.6).
 */
public class ImmichMediaServiceImplTest {
    private MockWebServer mServer;
    private ImmichPrefs mPrefs;
    private ImmichMediaServiceImpl mService;
    private String mApiBaseUrl;

    @Before
    public void setUp() throws Exception {
        ImmichPrefs.unhold();
        ImmichRetrofitHelper.reset();

        mServer = new MockWebServer();
        mServer.start();
        mApiBaseUrl = mServer.url("api/").toString();
        String origin = mServer.url("/").toString().replaceAll("/$", "");

        mPrefs = ImmichPrefs.createInMemory();
        mPrefs.setServerUrl(origin);
        mPrefs.setApiKey("play-key");

        ImmichApi api = new Retrofit.Builder()
                .baseUrl(mServer.url("api/"))
                .client(new OkHttpClient.Builder()
                        .addInterceptor(new Interceptor() {
                            @Override
                            public Response intercept(Chain chain) throws IOException {
                                Request original = chain.request();
                                Request.Builder builder = original.newBuilder();
                                String key = mPrefs.getApiKey();
                                if (key != null && original.header(ImmichHeaders.API_KEY) == null) {
                                    builder.header(ImmichHeaders.API_KEY, key);
                                }
                                return chain.proceed(builder.build());
                            }
                        })
                        .build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ImmichApi.class);

        mService = new ImmichMediaServiceImpl(mPrefs, api);
    }

    @After
    public void tearDown() throws Exception {
        mServer.shutdown();
        ImmichPrefs.unhold();
        ImmichRetrofitHelper.reset();
    }

    @Test
    public void getStreamInfoObserve_video_buildsPlaybackUrlWithoutExtraRequest() throws Exception {
        ImmichAsset asset = new ImmichAssetImpl(
                "vid-42", "holiday.mp4", ImmichAsset.TYPE_VIDEO, 90_000L,
                mApiBaseUrl + "assets/vid-42/thumbnail", "video/mp4");

        ImmichStreamInfo stream = mService.getStreamInfoObserve(asset).blockingFirst();

        assertNotNull(stream);
        assertEquals(mApiBaseUrl + "assets/vid-42/video/playback", stream.getUrl());
        assertEquals("video/mp4", stream.getContainer());
        assertEquals("play-key", stream.getApiKey());
        assertEquals(0, mServer.getRequestCount());

        MediaItemFormatInfo formatInfo = ImmichMediaItemFormatInfo.from(asset, stream);
        assertNotNull(formatInfo);
        assertFalse(formatInfo.getUrlFormats().isEmpty());
        assertEquals(stream.getUrl(), formatInfo.getUrlFormats().get(0).getUrl());
    }

    @Test
    public void getStreamInfoObserve_fetchesAssetWhenMimeMissing() throws Exception {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"id\":\"vid-9\","
                + "\"originalFileName\":\"clip.mp4\","
                + "\"type\":\"VIDEO\","
                + "\"duration\":1000,"
                + "\"originalMimeType\":\"video/mp4\""
                + "}"));

        ImmichAsset asset = new ImmichAssetImpl(
                "vid-9", "clip.mp4", ImmichAsset.TYPE_VIDEO, 1000L, null, null);

        ImmichStreamInfo stream = mService.getStreamInfoObserve(asset).blockingFirst();

        assertEquals(mApiBaseUrl + "assets/vid-9/video/playback", stream.getUrl());
        assertEquals("video/mp4", stream.getContainer());

        RecordedRequest request = mServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/assets/vid-9", request.getPath());
        assertEquals("play-key", request.getHeader(ImmichHeaders.API_KEY));
    }

    @Test
    public void getStreamInfoObserve_image_usesOriginalUrl() throws Exception {
        ImmichAsset asset = new ImmichAssetImpl(
                "img-1", "a.jpg", ImmichAsset.TYPE_IMAGE, 0L, null, "image/jpeg");

        ImmichStreamInfo stream = mService.getStreamInfoObserve(asset).blockingFirst();

        assertEquals(mApiBaseUrl + "assets/img-1/original", stream.getUrl());
        assertEquals("image/jpeg", stream.getContainer());
        assertEquals("play-key", stream.getApiKey());
        assertTrue(stream.getUrl().endsWith("/original"));
    }
}
