package de.developerleipzig.immichapi.service;

import de.developerleipzig.immichapi.library.ImmichAssetImpl;
import de.developerleipzig.immichapi.network.ImmichApi;
import de.developerleipzig.immichapi.network.ImmichHeaders;
import de.developerleipzig.immichapi.network.ImmichRetrofitHelper;
import de.developerleipzig.immichapi.prefs.ImmichPrefs;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset;
import de.developerleipzig.immichserviceinterfaces.data.ImmichStreamInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

        OkHttpClient client = new OkHttpClient.Builder()
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
                .build();

        ImmichApi api = new Retrofit.Builder()
                .baseUrl(mServer.url("api/"))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ImmichApi.class);

        mService = new ImmichMediaServiceImpl(mPrefs, api, client);
    }

    @After
    public void tearDown() throws Exception {
        mServer.shutdown();
        ImmichPrefs.unhold();
        ImmichRetrofitHelper.reset();
    }

    @Test
    public void getStreamInfoObserve_h264IsTvDirectPlay() throws Exception {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"id\":\"vid-42\","
                + "\"originalFileName\":\"holiday.mov\","
                + "\"type\":\"VIDEO\","
                + "\"duration\":90000,"
                + "\"originalMimeType\":\"video/quicktime\""
                + "}"));
        Buffer body = new Buffer();
        body.writeString("padding-avc1-padding", StandardCharsets.ISO_8859_1);
        mServer.enqueue(new MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setBody(body));

        ImmichAsset asset = new ImmichAssetImpl(
                "vid-42", "holiday.mov", ImmichAsset.TYPE_VIDEO, 90_000L,
                mApiBaseUrl + "assets/vid-42/thumbnail", "video/quicktime");

        ImmichStreamInfo stream = mService.getStreamInfoObserve(asset).blockingFirst();
        assertTrue(stream.hasEncodedVideo());
        assertEquals("video/mp4", stream.getContainer());
    }

    @Test
    public void getStreamInfoObserve_unknownCodecFailsOpenForDirectPlay() throws Exception {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"id\":\"vid-unknown\","
                + "\"originalFileName\":\"camera.mp4\","
                + "\"type\":\"VIDEO\","
                + "\"duration\":5000,"
                + "\"originalMimeType\":\"video/mp4\""
                + "}"));
        // No avc1/hev1 in the first window — typical when moov is at file end.
        Buffer body = new Buffer();
        body.writeString("ftypisom....mdat....no-codec-fourcc-here", StandardCharsets.ISO_8859_1);
        mServer.enqueue(new MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setBody(body));

        ImmichAsset asset = new ImmichAssetImpl(
                "vid-unknown", "camera.mp4", ImmichAsset.TYPE_VIDEO, 5000L, null, "video/mp4");

        ImmichStreamInfo stream = mService.getStreamInfoObserve(asset).blockingFirst();
        assertEquals("video/mp4", stream.getContainer());
        assertTrue(stream.hasEncodedVideo());
    }

    @Test
    public void getStreamInfoObserve_hevcNotTvDirectPlayOnLegacyTv() throws Exception {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"id\":\"vid-hevc\","
                + "\"originalFileName\":\"clip.mp4\","
                + "\"type\":\"VIDEO\","
                + "\"duration\":1000,"
                + "\"originalMimeType\":\"video/mp4\""
                + "}"));
        Buffer body = new Buffer();
        body.writeString("padding-hev1-padding", StandardCharsets.ISO_8859_1);
        mServer.enqueue(new MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setBody(body));

        ImmichAsset asset = new ImmichAssetImpl(
                "vid-hevc", "clip.mp4", ImmichAsset.TYPE_VIDEO, 1000L, null, "video/mp4");

        ImmichStreamInfo stream = mService.getStreamInfoObserve(asset).blockingFirst();
        assertEquals("video/hevc", stream.getContainer());
        assertFalse(stream.hasEncodedVideo());
    }

    @Test
    public void getStreamInfoObserve_image_usesOriginalUrl() throws Exception {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"id\":\"img-1\","
                + "\"originalFileName\":\"a.jpg\","
                + "\"type\":\"IMAGE\","
                + "\"originalMimeType\":\"image/jpeg\""
                + "}"));

        ImmichAsset asset = new ImmichAssetImpl(
                "img-1", "a.jpg", ImmichAsset.TYPE_IMAGE, 0L, null, "image/jpeg");

        ImmichStreamInfo stream = mService.getStreamInfoObserve(asset).blockingFirst();
        assertTrue(stream.getUrl().contains("/original"));
        assertFalse(stream.hasEncodedVideo());
    }
}
