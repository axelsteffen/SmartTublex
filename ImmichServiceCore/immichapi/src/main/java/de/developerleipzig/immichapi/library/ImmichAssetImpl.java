package de.developerleipzig.immichapi.library;

import de.developerleipzig.immichapi.network.ImmichUrlHelper;
import de.developerleipzig.immichapi.network.dto.AssetResponseDto;
import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset;

/**
 * Immutable {@link ImmichAsset} from Immich asset DTO.
 */
public final class ImmichAssetImpl implements ImmichAsset {
    private final String mId;
    private final String mTitle;
    private final String mType;
    private final long mDurationMs;
    private final String mThumbUrl;
    private final String mOriginalMimeType;

    public ImmichAssetImpl(String id, String title, String type, long durationMs,
                           String thumbUrl, String originalMimeType) {
        mId = id;
        mTitle = title;
        mType = type;
        mDurationMs = durationMs;
        mThumbUrl = thumbUrl;
        mOriginalMimeType = originalMimeType;
    }

    public static ImmichAssetImpl fromDto(AssetResponseDto dto, String apiBaseUrl) {
        return fromDto(dto, apiBaseUrl, null);
    }

    public static ImmichAssetImpl fromDto(AssetResponseDto dto, String apiBaseUrl, String apiKey) {
        if (dto == null || dto.id == null || dto.id.isEmpty()) {
            return null;
        }
        long durationMs = dto.duration != null ? Math.max(0L, dto.duration) : 0L;
        String title = dto.originalFileName != null && !dto.originalFileName.isEmpty()
                ? dto.originalFileName
                : dto.id;
        return new ImmichAssetImpl(
                dto.id,
                title,
                dto.type != null ? dto.type : TYPE_OTHER,
                durationMs,
                ImmichUrlHelper.assetThumbnailUrl(apiBaseUrl, dto.id, apiKey),
                dto.originalMimeType);
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
    public String getType() {
        return mType;
    }

    @Override
    public long getDurationMs() {
        return mDurationMs;
    }

    @Override
    public String getThumbUrl() {
        return mThumbUrl;
    }

    @Override
    public String getOriginalMimeType() {
        return mOriginalMimeType;
    }

    @Override
    public boolean isVideo() {
        return TYPE_VIDEO.equalsIgnoreCase(mType);
    }
}
