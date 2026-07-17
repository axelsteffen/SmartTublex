package com.liskovsoft.immichapi.adapter;

import androidx.annotation.Nullable;

import com.liskovsoft.immichserviceinterfaces.data.ImmichAsset;
import com.liskovsoft.immichserviceinterfaces.data.ImmichStreamInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemStoryboard;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;

/**
 * Fork-only adapter: maps {@link ImmichStreamInfo} (+ asset metadata) to MSC
 * {@link MediaItemFormatInfo} so existing ExoPlayer loaders can open the stream.
 * <p>
 * Direct URL → UrlFormats. Callers must attach {@code x-api-key} from
 * {@link ImmichStreamInfo#getApiKey()} when fetching media (Phase 3 bridge).
 */
public final class ImmichMediaItemFormatInfo implements MediaItemFormatInfo {
    private final String mVideoId;
    private final String mTitle;
    private final String mLengthSeconds;
    private final String mStreamUrl;
    private final String mContainer;
    private final String mApiKey;
    private final List<MediaFormat> mUrlFormats;
    private String mClickTrackingParams;

    private ImmichMediaItemFormatInfo(
            String videoId,
            String title,
            String lengthSeconds,
            String streamUrl,
            String container,
            String apiKey,
            List<MediaFormat> urlFormats) {
        mVideoId = videoId;
        mTitle = title;
        mLengthSeconds = lengthSeconds;
        mStreamUrl = streamUrl;
        mContainer = container;
        mApiKey = apiKey;
        mUrlFormats = urlFormats;
    }

    @Nullable
    public static ImmichMediaItemFormatInfo from(
            @Nullable ImmichAsset asset, @Nullable ImmichStreamInfo stream) {
        if (asset == null || stream == null) {
            return null;
        }
        String url = stream.getUrl();
        if (url == null || url.isEmpty()) {
            return null;
        }
        String assetId = asset.getId();
        if (assetId == null || assetId.isEmpty()) {
            return null;
        }

        long durationMs = asset.getDurationMs();
        String lengthSeconds = durationMs > 0
                ? String.valueOf(durationMs / 1000L)
                : null;

        return new ImmichMediaItemFormatInfo(
                assetId,
                asset.getTitle(),
                lengthSeconds,
                url,
                stream.getContainer(),
                stream.getApiKey(),
                ImmichMediaFormat.singletonList(url, stream.getContainer()));
    }

    public String getStreamUrl() {
        return mStreamUrl;
    }

    /** API key for ExoPlayer / Glide request headers (Phase 3). */
    @Nullable
    public String getApiKey() {
        return mApiKey;
    }

    @Nullable
    public String getContainer() {
        return mContainer;
    }

    @Override
    public List<MediaFormat> getAdaptiveFormats() {
        return null;
    }

    @Override
    public List<MediaFormat> getUrlFormats() {
        return mUrlFormats;
    }

    @Override
    public List<MediaSubtitle> getSubtitles() {
        return null;
    }

    @Override
    public String getHlsManifestUrl() {
        return null;
    }

    @Override
    public String getDashManifestUrl() {
        return null;
    }

    @Override
    public String getLengthSeconds() {
        return mLengthSeconds;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public String getAuthor() {
        return null;
    }

    @Override
    public String getViewCount() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getVideoId() {
        return mVideoId;
    }

    @Override
    public String getChannelId() {
        return null;
    }

    // getCategory() omitted: MediaItemFormatInfo in upstream SmartTube APK has no category field.

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public boolean isLiveContent() {
        return false;
    }

    @Override
    public boolean containsMedia() {
        return containsUrlFormats();
    }

    @Override
    public boolean containsSabrFormats() {
        return false;
    }

    @Override
    public boolean containsDashFormats() {
        return false;
    }

    @Override
    public boolean containsHlsUrl() {
        return false;
    }

    @Override
    public boolean containsDashUrl() {
        return false;
    }

    @Override
    public boolean containsUrlFormats() {
        return mUrlFormats != null && !mUrlFormats.isEmpty();
    }

    @Override
    public boolean hasExtendedHlsFormats() {
        return false;
    }

    @Override
    public float getVolumeLevel() {
        return 1.0f;
    }

    @Override
    public InputStream createMpdStream() {
        return null;
    }

    @Override
    public Observable<InputStream> createMpdStreamObservable() {
        return Observable.empty();
    }

    @Override
    public List<String> createUrlList() {
        if (!containsUrlFormats()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(mStreamUrl);
    }

    @Override
    public MediaItemStoryboard createStoryboard() {
        return null;
    }

    @Override
    public boolean isUnplayable() {
        return !containsMedia();
    }

    @Override
    public boolean isUnknownError() {
        return false;
    }

    @Override
    public String getPlayabilityReason() {
        return null;
    }

    @Override
    public boolean isStreamSeekable() {
        return true;
    }

    @Override
    public String getStartTimestamp() {
        return null;
    }

    @Override
    public String getUploadDate() {
        return null;
    }

    @Override
    public long getStartTimeMs() {
        return 0;
    }

    @Override
    public int getStartSegmentNum() {
        return 0;
    }

    @Override
    public int getSegmentDurationUs() {
        return 0;
    }

    @Override
    public String getPaidContentText() {
        return null;
    }

    @Override
    public String getVideoPlaybackUstreamerConfig() {
        return null;
    }

    @Override
    public String getServerAbrStreamingUrl() {
        return null;
    }

    @Override
    public String getPoToken() {
        return null;
    }

    @Override
    public String getVisitorCookie() {
        return null;
    }

    @Override
    public ClientInfo getClientInfo() {
        return null;
    }

    @Override
    public boolean isSynced() {
        return true;
    }

    @Override
    public boolean isAuth() {
        return true;
    }

    @Override
    public String getEventId() {
        return null;
    }

    @Override
    public String getVisitorMonitoringData() {
        return null;
    }

    @Override
    public String getOfParam() {
        return null;
    }

    @Override
    public String getClickTrackingParams() {
        return mClickTrackingParams;
    }

    @Override
    public void setClickTrackingParams(String clickTrackingParams) {
        mClickTrackingParams = clickTrackingParams;
    }

    @Override
    public boolean isCacheActual() {
        return true;
    }

    @Override
    public void sync(MediaItemFormatInfo formatInfo) {
        // no-op: Immich format info is built once from a resolved stream
    }
}
