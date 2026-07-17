package com.liskovsoft.immichapi.service;

import com.liskovsoft.immichapi.library.ImmichAlbumImpl;
import com.liskovsoft.immichapi.library.ImmichAssetImpl;
import com.liskovsoft.immichapi.library.ImmichPage;
import com.liskovsoft.immichapi.network.ImmichApi;
import com.liskovsoft.immichapi.network.ImmichRetrofitHelper;
import com.liskovsoft.immichapi.network.ImmichUrlHelper;
import com.liskovsoft.immichapi.network.dto.AlbumResponseDto;
import com.liskovsoft.immichapi.network.dto.AssetResponseDto;
import com.liskovsoft.immichapi.network.dto.MetadataSearchDto;
import com.liskovsoft.immichapi.network.dto.SearchResponseDto;
import com.liskovsoft.immichapi.prefs.ImmichPrefs;
import com.liskovsoft.immichserviceinterfaces.ImmichLibraryService;
import com.liskovsoft.immichserviceinterfaces.data.ImmichAlbum;
import com.liskovsoft.immichserviceinterfaces.data.ImmichAsset;
import com.liskovsoft.immichserviceinterfaces.data.ImmichAssetPage;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import retrofit2.Response;

/**
 * Lists Immich albums and paginated assets via search/metadata.
 */
public class ImmichLibraryServiceImpl implements ImmichLibraryService {
    private static final String TAG = ImmichLibraryServiceImpl.class.getSimpleName();
    static final int DEFAULT_PAGE_SIZE = 50;

    private final ImmichPrefs mPrefs;
    private final ImmichApi mApi;
    private final int mPageSize;

    public ImmichLibraryServiceImpl() {
        this(null, null, DEFAULT_PAGE_SIZE);
    }

    /** Package-visible for tests. */
    ImmichLibraryServiceImpl(ImmichPrefs prefs, ImmichApi api, int pageSize) {
        mPrefs = prefs;
        mApi = api;
        mPageSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
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
    public Observable<List<ImmichAlbum>> getAlbumsObserve() {
        return Observable.fromCallable(this::fetchAlbums);
    }

    @Override
    public Observable<ImmichAssetPage> getAlbumAssetsPageObserve(ImmichAlbum album, int offset) {
        return Observable.fromCallable(() -> fetchAlbumAssetsPage(album, offset));
    }

    @Override
    public Observable<ImmichAssetPage> getRecentVideosPageObserve(int offset) {
        return Observable.fromCallable(() -> fetchRecentVideosPage(offset));
    }

    private List<ImmichAlbum> fetchAlbums() throws IOException {
        Response<List<AlbumResponseDto>> response = api().getAllAlbums().execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException(formatFailure("albums", response));
        }
        String base = apiBaseUrl();
        List<ImmichAlbum> result = new ArrayList<>();
        for (AlbumResponseDto dto : response.body()) {
            ImmichAlbum album = ImmichAlbumImpl.fromDto(dto, base);
            if (album != null) {
                result.add(album);
            }
        }
        Log.d(TAG, "Fetched " + result.size() + " albums");
        return result;
    }

    private ImmichAssetPage fetchAlbumAssetsPage(ImmichAlbum album, int offset) throws IOException {
        if (album == null || album.getId() == null || album.getId().isEmpty()) {
            throw new IllegalArgumentException("album required");
        }
        MetadataSearchDto body = new MetadataSearchDto();
        body.albumIds = Collections.singletonList(album.getId());
        body.page = offsetToPage(offset);
        body.size = mPageSize;
        body.order = "desc";
        return executeSearch(body, offset);
    }

    private ImmichAssetPage fetchRecentVideosPage(int offset) throws IOException {
        MetadataSearchDto body = new MetadataSearchDto();
        body.type = ImmichAsset.TYPE_VIDEO;
        body.page = offsetToPage(offset);
        body.size = mPageSize;
        body.order = "desc";
        return executeSearch(body, offset);
    }

    private ImmichAssetPage executeSearch(MetadataSearchDto body, int offset) throws IOException {
        Response<SearchResponseDto> response = api().searchMetadata(body).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException(formatFailure("search/metadata", response));
        }
        SearchResponseDto.SearchAssetResponseDto assets = response.body().assets;
        String base = apiBaseUrl();
        List<ImmichAsset> items = new ArrayList<>();
        int total = -1;
        if (assets != null) {
            total = assets.total;
            if (assets.items != null) {
                for (AssetResponseDto dto : assets.items) {
                    ImmichAsset asset = ImmichAssetImpl.fromDto(dto, base);
                    if (asset != null) {
                        items.add(asset);
                    }
                }
            }
        }
        return new ImmichPage(items, Math.max(0, offset), total, mPageSize);
    }

    private int offsetToPage(int offset) {
        int safe = Math.max(0, offset);
        return (safe / mPageSize) + 1;
    }

    private static String formatFailure(String path, Response<?> response) {
        return "Immich " + path + " failed: HTTP " + response.code();
    }
}
