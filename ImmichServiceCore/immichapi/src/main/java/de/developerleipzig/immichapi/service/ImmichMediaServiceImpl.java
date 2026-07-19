package de.developerleipzig.immichapi.service;

import de.developerleipzig.immichapi.media.ImmichPlaybackProbe;
import de.developerleipzig.immichapi.media.ImmichStreamInfoImpl;
import de.developerleipzig.immichapi.network.ImmichApi;
import de.developerleipzig.immichapi.network.ImmichRetrofitHelper;
import de.developerleipzig.immichapi.network.ImmichUrlHelper;
import de.developerleipzig.immichapi.network.dto.AssetResponseDto;
import de.developerleipzig.immichapi.prefs.ImmichPrefs;
import de.developerleipzig.immichserviceinterfaces.ImmichMediaService;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset;
import de.developerleipzig.immichserviceinterfaces.data.ImmichStreamInfo;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;

import io.reactivex.Observable;
import okhttp3.OkHttpClient;
import retrofit2.Response;

/**
 * Resolves Immich playback URLs for video (and original for other types).
 */
public class ImmichMediaServiceImpl implements ImmichMediaService {
    private static final String TAG = ImmichMediaServiceImpl.class.getSimpleName();

    private final ImmichPrefs mPrefs;
    private final ImmichApi mApi;
    private final OkHttpClient mHttpClient;

    public ImmichMediaServiceImpl() {
        this(null, null, null);
    }

    /** Package-visible for tests. */
    ImmichMediaServiceImpl(ImmichPrefs prefs, ImmichApi api) {
        this(prefs, api, null);
    }

    /** Package-visible for tests (inject probe client). */
    ImmichMediaServiceImpl(ImmichPrefs prefs, ImmichApi api, OkHttpClient httpClient) {
        mPrefs = prefs;
        mApi = api;
        mHttpClient = httpClient;
    }

    private ImmichPrefs prefs() {
        return mPrefs != null ? mPrefs : ImmichPrefs.instance();
    }

    private ImmichApi api() {
        return mApi != null ? mApi : ImmichRetrofitHelper.createApi(ImmichApi.class);
    }

    private OkHttpClient httpClient() {
        return mHttpClient != null ? mHttpClient : ImmichRetrofitHelper.client();
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
        String apiKey = prefs().getApiKey();
        String mime = asset.getOriginalMimeType();
        boolean video = asset.isVideo();
        // Legacy Immich versions exposed encodedVideoPath; current OpenAPI does not.
        boolean legacyEncodedPath = false;

        Response<AssetResponseDto> response = api().getAsset(asset.getId()).execute();
        if (response.isSuccessful() && response.body() != null) {
            AssetResponseDto dto = response.body();
            if (dto.originalMimeType != null && !dto.originalMimeType.isEmpty()) {
                mime = dto.originalMimeType;
            }
            if (dto.type != null) {
                video = ImmichAsset.TYPE_VIDEO.equalsIgnoreCase(dto.type);
            }
            legacyEncodedPath = dto.encodedVideoPath != null && !dto.encodedVideoPath.isEmpty();
        }

        String url = video
                ? ImmichUrlHelper.assetVideoPlaybackUrl(base, asset.getId(), apiKey)
                : ImmichUrlHelper.assetOriginalUrl(base, asset.getId(), apiKey);

        if (url == null || url.isEmpty()) {
            throw new IOException("Unable to build Immich stream URL for " + asset.getId());
        }

        String container;
        boolean tvDirectPlayOk;
        if (video) {
            ImmichPlaybackProbe.Result probe = ImmichPlaybackProbe.probe(httpClient(), url);
            String playbackType = probe != null ? probe.contentType : null;
            ImmichPlaybackProbe.VideoCodecHint codec =
                    probe != null ? probe.codecHint : ImmichPlaybackProbe.VideoCodecHint.UNKNOWN;

            if (playbackType != null && !playbackType.isEmpty()) {
                container = playbackType;
            } else if (legacyEncodedPath) {
                container = "video/mp4";
            } else if (mime != null && !mime.isEmpty()) {
                container = mime;
            } else {
                container = "video/mp4";
            }

            // Refuse only when sniff positively finds HEVC/AV1/VP9.
            // UNKNOWN (moov often at file end) and probe failures must fail open —
            // otherwise previously playable H.264 videos are blocked by preflight.
            if (probe != null && probe.isLikelyNeedsTranscode()) {
                tvDirectPlayOk = false;
                if (codec == ImmichPlaybackProbe.VideoCodecHint.HEVC) {
                    container = "video/hevc";
                } else if (codec == ImmichPlaybackProbe.VideoCodecHint.AV1) {
                    container = "video/av1";
                } else if (codec == ImmichPlaybackProbe.VideoCodecHint.VP9) {
                    container = "video/webm";
                }
            } else {
                tvDirectPlayOk = true;
            }

            Log.i(TAG, "Stream assetId=" + asset.getId()
                    + " mime=" + container
                    + " codec=" + codec
                    + " tvDirectPlay=" + tvDirectPlayOk
                    + " legacyEncodedPath=" + legacyEncodedPath
                    + " playUrl=" + redactApiKey(url));
            if (!tvDirectPlayOk) {
                Log.w(TAG, "TV Direct Play skipped assetId=" + asset.getId()
                        + " codec=" + codec
                        + " — Immich must serve H.264 (accepted codecs / re-transcode)");
            }
        } else {
            container = mime != null && !mime.isEmpty() ? mime : "application/octet-stream";
            tvDirectPlayOk = false;
            Log.d(TAG, "Stream assetId=" + asset.getId() + " video=false mime=" + container);
        }

        return new ImmichStreamInfoImpl(url, container, prefs().getApiKey(), tvDirectPlayOk);
    }

    /** Keeps play URLs readable in logcat without leaking the API key. */
    private static String redactApiKey(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        return url.replaceAll("([?&]apiKey=)[^&]*", "$1***");
    }
}
