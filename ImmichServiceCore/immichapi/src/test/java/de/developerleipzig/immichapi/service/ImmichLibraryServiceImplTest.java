package de.developerleipzig.immichapi.service;

import de.developerleipzig.immichapi.library.ImmichAlbumImpl;
import de.developerleipzig.immichapi.library.ImmichPage;
import de.developerleipzig.immichapi.network.ImmichApi;
import de.developerleipzig.immichapi.network.ImmichHeaders;
import de.developerleipzig.immichapi.network.ImmichRetrofitHelper;
import de.developerleipzig.immichapi.prefs.ImmichPrefs;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAlbum;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAssetPage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * MockWebServer coverage for Immich albums + metadata search (Phase 1.3–1.4 / 1.6).
 */
public class ImmichLibraryServiceImplTest {
    private MockWebServer mServer;
    private ImmichPrefs mPrefs;
    private ImmichLibraryServiceImpl mService;

    @Before
    public void setUp() throws Exception {
        ImmichPrefs.unhold();
        ImmichRetrofitHelper.reset();

        mServer = new MockWebServer();
        mServer.start();
        String origin = mServer.url("/").toString().replaceAll("/$", "");

        mPrefs = ImmichPrefs.createInMemory();
        mPrefs.setServerUrl(origin);
        mPrefs.setApiKey("server-key");

        ImmichApi api = new Retrofit.Builder()
                .baseUrl(mServer.url("api/"))
                .client(new OkHttpClient.Builder()
                        .addInterceptor(new Interceptor() {
                            @Override
                            public Response intercept(Chain chain) throws IOException {
                                Request original = chain.request();
                                Request.Builder builder = original.newBuilder()
                                        .header(ImmichHeaders.ACCEPT, ImmichHeaders.ACCEPT_JSON);
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

        mService = new ImmichLibraryServiceImpl(mPrefs, api, 2);
    }

    @After
    public void tearDown() throws Exception {
        mServer.shutdown();
        ImmichPrefs.unhold();
        ImmichRetrofitHelper.reset();
    }

    @Test
    public void getAlbumsObserve_mapsAlbumsAndSendsApiKey() throws Exception {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("["
                + "{\"id\":\"alb-1\",\"albumName\":\"Vacation\",\"assetCount\":3,"
                + "\"albumThumbnailAssetId\":\"thumb-1\"},"
                + "{\"id\":\"alb-2\",\"albumName\":\"Kids\",\"assetCount\":0}"
                + "]"));

        List<ImmichAlbum> albums = mService.getAlbumsObserve().blockingFirst();

        assertEquals(2, albums.size());
        assertEquals("alb-1", albums.get(0).getId());
        assertEquals("Vacation", albums.get(0).getTitle());
        assertEquals(3, albums.get(0).getAssetCount());
        assertTrue(albums.get(0).getThumbUrl().contains("assets/thumb-1/thumbnail"));
        assertTrue(albums.get(0).getThumbUrl().contains("apiKey=server-key"));

        RecordedRequest request = mServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/albums", request.getPath());
        assertEquals("server-key", request.getHeader(ImmichHeaders.API_KEY));
    }

    @Test
    public void getRecentVideosPageObserve_postsSearchAndMapsAssets() throws Exception {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"assets\":{"
                + "\"total\":3,\"count\":2,\"items\":["
                + "{\"id\":\"vid-1\",\"originalFileName\":\"clip.mp4\",\"type\":\"VIDEO\","
                + "\"duration\":12500,\"originalMimeType\":\"video/mp4\"},"
                + "{\"id\":\"vid-2\",\"originalFileName\":\"trip.mov\",\"type\":\"VIDEO\","
                + "\"duration\":60000,\"originalMimeType\":\"video/quicktime\"}"
                + "]"
                + "}}"));

        ImmichAssetPage page = mService.getRecentVideosPageObserve(0).blockingFirst();

        assertEquals(0, page.getOffset());
        assertEquals(3, page.getTotalSize());
        assertEquals(2, page.getItems().size());
        ImmichAsset first = page.getItems().get(0);
        assertEquals("vid-1", first.getId());
        assertEquals("clip.mp4", first.getTitle());
        assertTrue(first.isVideo());
        assertEquals(12500L, first.getDurationMs());
        assertTrue(first.getThumbUrl().contains("assets/vid-1/thumbnail"));
        assertTrue(first.getThumbUrl().contains("apiKey=server-key"));
        assertEquals(2, ((ImmichPage) page).getNextOffset());

        RecordedRequest request = mServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/search/metadata", request.getPath());
        assertEquals("POST", request.getMethod());
        assertEquals("server-key", request.getHeader(ImmichHeaders.API_KEY));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"type\":\"VIDEO\""));
        assertTrue(body.contains("\"page\":1"));
        assertTrue(body.contains("\"size\":2"));
    }

    @Test
    public void getAlbumAssetsPageObserve_filtersByAlbumId() throws Exception {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"assets\":{"
                + "\"total\":1,\"count\":1,\"items\":["
                + "{\"id\":\"img-1\",\"originalFileName\":\"a.jpg\",\"type\":\"IMAGE\","
                + "\"duration\":0,\"originalMimeType\":\"image/jpeg\"}"
                + "]"
                + "}}"));

        ImmichAlbum album = new ImmichAlbumImpl("alb-9", "Album", 1, null, null);
        ImmichAssetPage page = mService.getAlbumAssetsPageObserve(album, 0).blockingFirst();

        assertEquals(1, page.getItems().size());
        assertEquals("img-1", page.getItems().get(0).getId());
        assertEquals(-1, ((ImmichPage) page).getNextOffset());

        RecordedRequest request = mServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"albumIds\":[\"alb-9\"]"));
        assertTrue(body.contains("\"page\":1"));
    }

    @Test
    public void getPhotoYearsObserve_mapsYearBucketsNewestFirst() throws Exception {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("["
                + "{\"timeBucket\":\"2023-01-01T00:00:00.000Z\",\"count\":2},"
                + "{\"timeBucket\":\"2025-01-01T00:00:00.000Z\",\"count\":5},"
                + "{\"timeBucket\":\"2024-06-01T00:00:00.000Z\",\"count\":0},"
                + "{\"timeBucket\":\"bad\",\"count\":1}"
                + "]"));

        List<Integer> years = mService.getPhotoYearsObserve().blockingFirst();

        assertEquals(2, years.size());
        assertEquals(Integer.valueOf(2025), years.get(0));
        assertEquals(Integer.valueOf(2023), years.get(1));

        RecordedRequest request = mServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertTrue(request.getPath().contains("/api/timeline/buckets"));
        assertTrue(request.getPath().contains("size=YEAR"));
    }

    @Test
    public void getAssetsForYearPageObserve_sendsTakenBounds() throws Exception {
        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"assets\":{"
                + "\"total\":1,\"count\":1,\"items\":["
                + "{\"id\":\"img-y\",\"originalFileName\":\"y.jpg\",\"type\":\"IMAGE\","
                + "\"duration\":0,\"originalMimeType\":\"image/jpeg\"}"
                + "]"
                + "}}"));

        ImmichAssetPage page = mService.getAssetsForYearPageObserve(2024, 0).blockingFirst();
        assertEquals(1, page.getItems().size());

        RecordedRequest request = mServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"takenAfter\":\"2024-01-01T00:00:00.000Z\""));
        assertTrue(body.contains("\"takenBefore\":\"2025-01-01T00:00:00.000Z\""));
    }

    @Test
    public void parseYear_readsIsoPrefix() {
        assertEquals(Integer.valueOf(2024), ImmichLibraryServiceImpl.parseYear("2024-01-01T00:00:00.000Z"));
        assertEquals(null, ImmichLibraryServiceImpl.parseYear("x"));
        assertEquals(null, ImmichLibraryServiceImpl.parseYear(null));
    }
}
