package de.developerleipzig.immichapi.prefs;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent Immich server URL, API key, and cached user profile.
 */
public final class ImmichPrefs {
    private static final String SHARED_PREFERENCES_NAME = ImmichPrefs.class.getName();
    private static final String SERVER_URL = "immich_server_url";
    private static final String API_KEY = "immich_api_key";
    private static final String USER_ID = "immich_user_id";
    private static final String USER_NAME = "immich_user_name";
    private static final String USER_EMAIL = "immich_user_email";
    private static final String VALIDATED = "immich_validated";

    @SuppressLint("StaticFieldLeak")
    private static ImmichPrefs sInstance;

    private final Store mStore;

    private ImmichPrefs(Store store) {
        mStore = store;
    }

    public static synchronized ImmichPrefs instance() {
        if (sInstance == null) {
            Context context = GlobalPreferences.context();
            if (context == null) {
                throw new IllegalStateException(
                        "ImmichPrefs requires GlobalPreferences (or instance(Context)) first");
            }
            sInstance = new ImmichPrefs(sharedStore(context));
        }
        return sInstance;
    }

    public static synchronized ImmichPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new ImmichPrefs(sharedStore(context.getApplicationContext()));
        }
        return sInstance;
    }

    /** In-memory prefs for JVM unit tests (no Android SharedPreferences). */
    public static ImmichPrefs createInMemory() {
        return new ImmichPrefs(new MemoryStore());
    }

    /** Clears singleton — for unit tests only. */
    public static synchronized void unhold() {
        sInstance = null;
    }

    private static Store sharedStore(Context context) {
        final SharedPreferencesBase prefs = new SharedPreferencesBase(context, SHARED_PREFERENCES_NAME) {};
        return new Store() {
            @Override
            public String getString(String key, String defValue) {
                return prefs.getString(key, defValue);
            }

            @Override
            public void putString(String key, String value) {
                prefs.putString(key, value);
            }

            @Override
            public boolean getBoolean(String key, boolean defValue) {
                return prefs.getBoolean(key, defValue);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                prefs.putBoolean(key, value);
            }
        };
    }

    @Nullable
    public String getServerUrl() {
        return emptyToNull(mStore.getString(SERVER_URL, null));
    }

    public void setServerUrl(@Nullable String url) {
        mStore.putString(SERVER_URL, nullToEmpty(url));
        setValidated(false);
    }

    @Nullable
    public String getApiKey() {
        return emptyToNull(mStore.getString(API_KEY, null));
    }

    public void setApiKey(@Nullable String apiKey) {
        mStore.putString(API_KEY, nullToEmpty(apiKey));
        setValidated(false);
    }

    public boolean isValidated() {
        return mStore.getBoolean(VALIDATED, false);
    }

    public void setValidated(boolean validated) {
        mStore.putBoolean(VALIDATED, validated);
    }

    @Nullable
    public String getUserId() {
        return emptyToNull(mStore.getString(USER_ID, null));
    }

    @Nullable
    public String getUserName() {
        return emptyToNull(mStore.getString(USER_NAME, null));
    }

    @Nullable
    public String getUserEmail() {
        return emptyToNull(mStore.getString(USER_EMAIL, null));
    }

    public void setUserProfile(@Nullable String id, @Nullable String name, @Nullable String email) {
        mStore.putString(USER_ID, nullToEmpty(id));
        mStore.putString(USER_NAME, nullToEmpty(name));
        mStore.putString(USER_EMAIL, nullToEmpty(email));
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

    private interface Store {
        String getString(String key, String defValue);

        void putString(String key, String value);

        boolean getBoolean(String key, boolean defValue);

        void putBoolean(String key, boolean value);
    }

    private static final class MemoryStore implements Store {
        private final Map<String, String> mStrings = new HashMap<>();
        private final Map<String, Boolean> mBooleans = new HashMap<>();

        @Override
        public String getString(String key, String defValue) {
            return mStrings.containsKey(key) ? mStrings.get(key) : defValue;
        }

        @Override
        public void putString(String key, String value) {
            mStrings.put(key, value);
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return mBooleans.containsKey(key) ? mBooleans.get(key) : defValue;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            mBooleans.put(key, value);
        }
    }
}
