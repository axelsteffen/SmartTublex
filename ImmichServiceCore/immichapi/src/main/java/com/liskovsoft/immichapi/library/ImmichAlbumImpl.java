package com.liskovsoft.immichapi.library;

import com.liskovsoft.immichapi.network.ImmichUrlHelper;
import com.liskovsoft.immichapi.network.dto.AlbumResponseDto;
import com.liskovsoft.immichserviceinterfaces.data.ImmichAlbum;

/**
 * Immutable {@link ImmichAlbum} from Immich album DTO.
 */
public final class ImmichAlbumImpl implements ImmichAlbum {
    private final String mId;
    private final String mTitle;
    private final int mAssetCount;
    private final String mThumbnailAssetId;
    private final String mThumbUrl;

    public ImmichAlbumImpl(String id, String title, int assetCount,
                           String thumbnailAssetId, String thumbUrl) {
        mId = id;
        mTitle = title;
        mAssetCount = assetCount;
        mThumbnailAssetId = thumbnailAssetId;
        mThumbUrl = thumbUrl;
    }

    public static ImmichAlbumImpl fromDto(AlbumResponseDto dto, String apiBaseUrl) {
        if (dto == null || dto.id == null || dto.id.isEmpty()) {
            return null;
        }
        String thumbUrl = ImmichUrlHelper.assetThumbnailUrl(apiBaseUrl, dto.albumThumbnailAssetId);
        return new ImmichAlbumImpl(
                dto.id,
                dto.albumName != null ? dto.albumName : dto.id,
                dto.assetCount,
                dto.albumThumbnailAssetId,
                thumbUrl);
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public int getAssetCount() {
        return mAssetCount;
    }

    @Override
    public String getThumbnailAssetId() {
        return mThumbnailAssetId;
    }

    @Override
    public String getThumbUrl() {
        return mThumbUrl;
    }
}
