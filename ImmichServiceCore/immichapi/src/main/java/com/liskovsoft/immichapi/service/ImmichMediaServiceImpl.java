package com.liskovsoft.immichapi.service;

import com.liskovsoft.immichapi.media.ImmichStreamInfoImpl;
import com.liskovsoft.immichapi.network.ImmichApi;
import com.liskovsoft.immichapi.network.ImmichRetrofitHelper;
import com.liskovsoft.immichapi.network.ImmichUrlHelper;
import com.liskovsoft.immichapi.network.dto.AssetResponseDto;
import com.liskovsoft.immichapi.prefs.ImmichPrefs;
import com.liskovsoft.immichserviceinterfaces.ImmichMediaService;
import com.liskovsoft.immichserviceinterfaces.data.ImmichAsset;
import com.liskovsoft.immichserviceinterfaces.data.ImmichStreamInfo;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;

import io.reactivex.Observable;
import retrofit2.Response;

/**
 * Resolves Immich playback URLs for video (and original for other types).
 */
public class ImmichMediaServiceImpl implements ImmichMediaService {
    private static final String TAG = ImmichMediaServiceImpl.class.getSimpleName();

    private final ImmichPrefs mPrefs;
    private final ImmichApi mApi;

    public ImmichMediaServiceImpl() {
        this(null, null);
    }

    /** Package-visible for tests. */
    ImmichMediaServiceImpl(ImmichPrefs prefs, ImmichApi api) {
        mPrefs = prefs;
        mApi = api;
    }

    private ImmichPrefs prefs() {
        return mPrefs != null ? mPrefs : ImmichPrefs.instance();
    }

    private ImmichApi api() {
        return mApi != null ? mApi : ImmichRetrofitHelper.createApi(ImmichApi.class);
    }

    private String apiBaseUrl() {
        String serverUrl = prefs().getServerUrl();
        if (serverUrl == null || serverUrl.isEmpty()) {
            throw new IllegalStateException("Immich server URL not configured");
        }
        return ImmichUrlHelper.normalizeApiBaseUrl(serverUrl);
    }

    @Override
    public Observable<ImmichStreamInfo> getStreamInfoObserve(ImmichAsset asset) {
        return Observable.fromCallable(() -> fetchStreamInfo(asset));
    }

    private ImmichStreamInfo fetchStreamInfo(ImmichAsset asset) throws IOException {
        if (asset == null || asset.getId() == null || asset.getId().isEmpty()) {
            throw new IllegalArgumentException("asset required");
        }

        String base = apiBaseUrl();
        String mime = asset.getOriginalMimeType();
        boolean video = asset.isVideo();

        // Refresh metadata when mime unknown.
        if (mime == null || mime.isEmpty()) {
            Response<AssetResponseDto> response = api().getAsset(asset.getId()).execute();
            if (response.isSuccessful() && response.body() != null) {
                AssetResponseDto dto = response.body();
                mime = dto.originalMimeType;
                if (dto.type != null) {
                    video = ImmichAsset.TYPE_VIDEO.equalsIgnoreCase(dto.type);
                }
            }
        }

        String url = video
                ? ImmichUrlHelper.assetVideoPlaybackUrl(base, asset.getId())
                : ImmichUrlHelper.assetOriginalUrl(base, asset.getId());

        if (url == null || url.isEmpty()) {
            throw new IOException("Unable to build Immich stream URL for " + asset.getId());
        }

        String container = mime != null && !mime.isEmpty()
                ? mime
                : (video ? "video/mp4" : "application/octet-stream");

        Log.d(TAG, "Stream URL for " + asset.getId() + " video=" + video);
        return new ImmichStreamInfoImpl(url, container, prefs().getApiKey());
    }
}
