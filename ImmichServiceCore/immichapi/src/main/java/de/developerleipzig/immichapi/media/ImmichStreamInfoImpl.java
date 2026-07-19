package de.developerleipzig.immichapi.media;

import de.developerleipzig.immichserviceinterfaces.data.ImmichStreamInfo;

/**
 * Immutable {@link ImmichStreamInfo}.
 */
public final class ImmichStreamInfoImpl implements ImmichStreamInfo {
    private final String mUrl;
    private final String mContainer;
    private final String mApiKey;
    private final boolean mHasEncodedVideo;

    public ImmichStreamInfoImpl(String url, String container, String apiKey, boolean hasEncodedVideo) {
        mUrl = url;
        mContainer = container;
        mApiKey = apiKey;
        mHasEncodedVideo = hasEncodedVideo;
    }

    @Override
    public String getUrl() {
        return mUrl;
    }

    @Override
    public String getContainer() {
        return mContainer;
    }

    @Override
    public String getApiKey() {
        return mApiKey;
    }

    @Override
    public boolean hasEncodedVideo() {
        return mHasEncodedVideo;
    }
}
