package com.liskovsoft.immichapi.media;

import com.liskovsoft.immichserviceinterfaces.data.ImmichStreamInfo;

/**
 * Immutable {@link ImmichStreamInfo}.
 */
public final class ImmichStreamInfoImpl implements ImmichStreamInfo {
    private final String mUrl;
    private final String mContainer;
    private final String mApiKey;

    public ImmichStreamInfoImpl(String url, String container, String apiKey) {
        mUrl = url;
        mContainer = container;
        mApiKey = apiKey;
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
}
