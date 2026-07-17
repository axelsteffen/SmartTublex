package de.developerleipzig.immichapi;

import android.content.Context;

import de.developerleipzig.immichapi.prefs.ImmichPrefs;
import de.developerleipzig.immichapi.service.ImmichLibraryServiceImpl;
import de.developerleipzig.immichapi.service.ImmichMediaServiceImpl;
import de.developerleipzig.immichapi.service.ImmichSignInServiceImpl;
import de.developerleipzig.immichserviceinterfaces.ImmichLibraryService;
import de.developerleipzig.immichserviceinterfaces.ImmichMediaService;
import de.developerleipzig.immichserviceinterfaces.ImmichSignInService;
import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * Fork-only entry point for Immich services.
 * Sign-in (URL + API key), album browse, and stream resolve are live.
 */
public final class ImmichServiceManager implements de.developerleipzig.immichserviceinterfaces.ImmichServiceManager {
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

    public static de.developerleipzig.immichserviceinterfaces.ImmichServiceManager instance() {
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
