package com.liskovsoft.immichapi.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;

/**
 * Persistent Immich server URL, API key, and cached user profile.
 */
public final class ImmichPrefs extends SharedPreferencesBase {
    private static final String SHARED_PREFERENCES_NAME = ImmichPrefs.class.getName();
    private static final String SERVER_URL = "immich_server_url";
    private static final String API_KEY = "immich_api_key";
    private static final String USER_ID = "immich_user_id";
    private static final String USER_NAME = "immich_user_name";
    private static final String USER_EMAIL = "immich_user_email";
    private static final String VALIDATED = "immich_validated";

    @SuppressLint("StaticFieldLeak")
    private static ImmichPrefs sInstance;

    private ImmichPrefs(Context context) {
        super(context, SHARED_PREFERENCES_NAME);
    }

    public static synchronized ImmichPrefs instance() {
        if (sInstance == null) {
            Context context = GlobalPreferences.context();
            if (context == null) {
                throw new IllegalStateException(
                        "ImmichPrefs requires GlobalPreferences (or instance(Context)) first");
            }
            sInstance = new ImmichPrefs(context);
        }
        return sInstance;
    }

    public static synchronized ImmichPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new ImmichPrefs(context.getApplicationContext());
        }
        return sInstance;
    }

    /** Clears singleton — for unit tests only. */
    public static synchronized void unhold() {
        sInstance = null;
    }

    @Nullable
    public String getServerUrl() {
        return emptyToNull(getString(SERVER_URL, null));
    }

    public void setServerUrl(@Nullable String url) {
        putString(SERVER_URL, nullToEmpty(url));
        setValidated(false);
    }

    @Nullable
    public String getApiKey() {
        return emptyToNull(getString(API_KEY, null));
    }

    public void setApiKey(@Nullable String apiKey) {
        putString(API_KEY, nullToEmpty(apiKey));
        setValidated(false);
    }

    public boolean isValidated() {
        return getBoolean(VALIDATED, false);
    }

    public void setValidated(boolean validated) {
        putBoolean(VALIDATED, validated);
    }

    @Nullable
    public String getUserId() {
        return emptyToNull(getString(USER_ID, null));
    }

    @Nullable
    public String getUserName() {
        return emptyToNull(getString(USER_NAME, null));
    }

    @Nullable
    public String getUserEmail() {
        return emptyToNull(getString(USER_EMAIL, null));
    }

    public void setUserProfile(@Nullable String id, @Nullable String name, @Nullable String email) {
        putString(USER_ID, nullToEmpty(id));
        putString(USER_NAME, nullToEmpty(name));
        putString(USER_EMAIL, nullToEmpty(email));
    }

    public void clear() {
        setServerUrl(null);
        setApiKey(null);
        setUserProfile(null, null, null);
        setValidated(false);
    }

    private static String nullToEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return value != null && !value.isEmpty() ? value : null;
    }
}
