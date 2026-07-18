package de.developerleipzig.immichapi.service;

import de.developerleipzig.immichapi.network.ImmichApi;
import de.developerleipzig.immichapi.network.ImmichHeaders;
import de.developerleipzig.immichapi.network.ImmichRetrofitHelper;
import de.developerleipzig.immichapi.prefs.ImmichPrefs;
import de.developerleipzig.immichserviceinterfaces.data.ImmichUser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * MockWebServer coverage for Immich URL + API-key auth (Phase 1.2 / 1.6).
 */
public class ImmichSignInServiceImplTest {
    private MockWebServer mServer;
    private ImmichPrefs mPrefs;
    private ImmichSignInServiceImpl mService;

    @Before
    public void setUp() throws Exception {
        ImmichPrefs.unhold();
        ImmichRetrofitHelper.reset();

        mPrefs = ImmichPrefs.createInMemory();
        mServer = new MockWebServer();
        mServer.start();

        ImmichApi api = new Retrofit.Builder()
                .baseUrl(mServer.url("api/"))
                .client(new OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ImmichApi.class);
        mService = new ImmichSignInServiceImpl(mPrefs, api);
    }

    @After
    public void tearDown() throws Exception {
        mServer.shutdown();
        ImmichPrefs.unhold();
        ImmichRetrofitHelper.reset();
    }

    @Test
    public void setServerUrl_normalizesAndClearsValidated() {
        mPrefs.setValidated(true);
        String origin = mServer.url("/").toString().replaceAll("/$", "");
        mService.setServerUrl(origin + "/api/");
        assertEquals(origin, mService.getServerUrl());
        assertFalse(mPrefs.isValidated());
    }

    @Test
    public void signOut_clearsCredentials() {
        mService.setServerUrl("https://immich.example");
        mService.setApiKey("key-abc");
        mPrefs.setValidated(true);

        mService.signOut();

        assertFalse(mService.isSigned());
        assertNull(mService.getServerUrl());
        assertNull(mService.getApiKey());
    }

    @Test
    public void validateObserve_storesProfileAndSetsValidated() throws Exception {
        String origin = mServer.url("/").toString().replaceAll("/$", "");
        mService.setServerUrl(origin);
        mService.setApiKey("test-api-key");

        mServer.enqueue(new MockResponse().setResponseCode(200).setBody("{"
                + "\"id\":\"user-1\","
                + "\"name\":\"Ada\","
                + "\"email\":\"ada@example.com\""
                + "}"));

        ImmichUser user = mService.validateObserve().blockingFirst();

        assertNotNull(user);
        assertEquals("user-1", user.getId());
        assertEquals("Ada", user.getName());
        assertEquals("ada@example.com", user.getEmail());
        assertTrue(mService.isSigned());
        assertEquals("user-1", mPrefs.getUserId());
        assertTrue(mPrefs.isValidated());

        RecordedRequest request = mServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/users/me", request.getPath());
        assertEquals("test-api-key", request.getHeader(ImmichHeaders.API_KEY));
    }

    @Test
    public void validateObserve_httpError_clearsValidated() throws Exception {
        mService.setServerUrl(mServer.url("/").toString().replaceAll("/$", ""));
        mService.setApiKey("bad-key");
        mPrefs.setValidated(true);

        mServer.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"Unauthorized\"}"));

        try {
            mService.validateObserve().blockingFirst();
            fail("expected error");
        } catch (RuntimeException expected) {
            Throwable cause = expected.getCause();
            assertTrue(cause instanceof IOException || expected.getMessage() != null);
        }
        assertFalse(mPrefs.isValidated());
        assertFalse(mService.isSigned());
    }
}
