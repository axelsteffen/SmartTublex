package com.liskovsoft.immichapi;

import android.content.Context;

import com.liskovsoft.immichapi.prefs.ImmichPrefs;
import com.liskovsoft.immichapi.service.ImmichLibraryServiceImpl;
import com.liskovsoft.immichapi.service.ImmichMediaServiceImpl;
import com.liskovsoft.immichapi.service.ImmichSignInServiceImpl;
import com.liskovsoft.immichserviceinterfaces.ImmichLibraryService;
import com.liskovsoft.immichserviceinterfaces.ImmichMediaService;
import com.liskovsoft.immichserviceinterfaces.ImmichSignInService;
import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * Fork-only entry point for Immich services.
 * Sign-in (URL + API key), album browse, and stream resolve are live.
 */
public final class ImmichServiceManager implements com.liskovsoft.immichserviceinterfaces.ImmichServiceManager {
    private static final String TAG = ImmichServiceManager.class.getSimpleName();
    private static ImmichServiceManager sInstance;

    private final ImmichSignInService mSignInService;
    private final ImmichLibraryService mLibraryService;
    private final ImmichMediaService mMediaService;

    private ImmichServiceManager() {
        Log.d(TAG, "Starting...");
        mSignInService = new ImmichSignInServiceImpl();
        mLibraryService = new ImmichLibraryServiceImpl();
        mMediaService = new ImmichMediaServiceImpl();
    }

    /**
     * Optional early init so {@link ImmichPrefs} has a Context before the first API call
     * (also covered once {@code GlobalPreferences} is ready).
     */
    public static void init(Context context) {
        if (context != null) {
            ImmichPrefs.instance(context);
        }
    }

    public static com.liskovsoft.immichserviceinterfaces.ImmichServiceManager instance() {
        if (sInstance == null) {
            sInstance = new ImmichServiceManager();
        }
        return sInstance;
    }

    @Override
    public ImmichSignInService getSignInService() {
        return mSignInService;
    }

    @Override
    public ImmichLibraryService getLibraryService() {
        return mLibraryService;
    }

    @Override
    public ImmichMediaService getMediaService() {
        return mMediaService;
    }
}
