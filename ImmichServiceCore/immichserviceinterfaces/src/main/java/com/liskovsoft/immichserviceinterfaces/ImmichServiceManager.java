package com.liskovsoft.immichserviceinterfaces;

/**
 * Fork-only facade for Immich services (analogous to mediaserviceinterfaces.ServiceManager /
 * PlexServiceManager).
 */
public interface ImmichServiceManager {
    ImmichSignInService getSignInService();

    ImmichLibraryService getLibraryService();

    ImmichMediaService getMediaService();
}
