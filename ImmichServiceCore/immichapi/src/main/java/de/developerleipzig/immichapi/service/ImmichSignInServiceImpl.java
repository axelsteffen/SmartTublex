package de.developerleipzig.immichapi.service;

import de.developerleipzig.immichapi.library.ImmichUserImpl;
import de.developerleipzig.immichapi.network.ImmichApi;
import de.developerleipzig.immichapi.network.ImmichRetrofitHelper;
import de.developerleipzig.immichapi.network.ImmichUrlHelper;
import de.developerleipzig.immichapi.network.dto.UserResponseDto;
import de.developerleipzig.immichapi.prefs.ImmichPrefs;
import de.developerleipzig.immichserviceinterfaces.ImmichSignInService;
import de.developerleipzig.immichserviceinterfaces.data.ImmichUser;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;

import io.reactivex.Observable;
import retrofit2.Response;

/**
 * Immich URL + API-key auth; validates against {@code GET /api/users/me}.
 */
public class ImmichSignInServiceImpl implements ImmichSignInService {
    private static final String TAG = ImmichSignInServiceImpl.class.getSimpleName();

    private final ImmichPrefs mPrefs;
    private final ImmichApi mApi;

    public ImmichSignInServiceImpl() {
        this(null, null);
    }

    /** Package-visible for tests. */
    ImmichSignInServiceImpl(ImmichPrefs prefs, ImmichApi api) {
        mPrefs = prefs;
        mApi = api;
    }

    private ImmichPrefs prefs() {
        return mPrefs != null ? mPrefs : ImmichPrefs.instance();
    }

    @Override
    public boolean isSigned() {
        String url = getServerUrl();
        String key = getApiKey();
        return url != null && !url.isEmpty()
                && key != null && !key.isEmpty()
                && prefs().isValidated();
    }

    @Override
    public String getServerUrl() {
        return prefs().getServerUrl();
    }

    @Override
    public void setServerUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            prefs().setServerUrl(null);
            return;
        }
        prefs().setServerUrl(ImmichUrlHelper.normalizeServerUrl(serverUrl.trim()));
        ImmichRetrofitHelper.reset();
    }

    @Override
    public String getApiKey() {
        return prefs().getApiKey();
    }

    @Override
    public void setApiKey(String apiKey) {
        prefs().setApiKey(apiKey != null ? apiKey.trim() : null);
    }

    @Override
    public void signOut() {
        prefs().clear();
        ImmichRetrofitHelper.reset();
    }

    @Override
    public Observable<ImmichUser> validateObserve() {
        return Observable.fromCallable(this::validate);
    }

    private ImmichUser validate() throws IOException {
        String serverUrl = getServerUrl();
        String apiKey = getApiKey();
        if (serverUrl == null || serverUrl.isEmpty()) {
            throw new IllegalStateException("Immich server URL required");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Immich API key required");
        }

        ImmichApi api = mApi != null
                ? mApi
                : ImmichRetrofitHelper.createApi(
                        ImmichUrlHelper.normalizeApiBaseUrl(serverUrl), ImmichApi.class);

        Response<UserResponseDto> response = api.getMyUser(apiKey).execute();
        if (!response.isSuccessful() || response.body() == null) {
            prefs().setValidated(false);
            throw new IOException(formatFailure("users/me", response));
        }

        UserResponseDto body = response.body();
        ImmichUser user = new ImmichUserImpl(body.id, body.name, body.email);
        prefs().setUserProfile(body.id, body.name, body.email);
        prefs().setValidated(true);
        Log.d(TAG, "Validated Immich user id=" + body.id);
        return user;
    }

    private static String formatFailure(String path, Response<?> response) {
        String msg = "Immich " + path + " failed: HTTP " + response.code();
        try {
            if (response.errorBody() != null) {
                String err = response.errorBody().string();
                if (err != null && !err.isEmpty()) {
                    return msg + " — " + err;
                }
            }
        } catch (IOException ignored) {
            // keep short message
        }
        return msg;
    }
}
