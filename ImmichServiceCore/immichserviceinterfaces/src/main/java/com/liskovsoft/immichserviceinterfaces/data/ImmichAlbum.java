package com.liskovsoft.immichserviceinterfaces.data;

public interface ImmichAlbum {
    String getId();

    String getTitle();

    int getAssetCount();

    /** Thumbnail asset UUID, or null. */
    String getThumbnailAssetId();

    /** Absolute thumbnail URL when base URL is known; may require auth header. */
    String getThumbUrl();
}
