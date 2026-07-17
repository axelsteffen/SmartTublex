package com.liskovsoft.immichserviceinterfaces.data;

/**
 * Immich photo or video asset.
 */
public interface ImmichAsset {
    String TYPE_IMAGE = "IMAGE";
    String TYPE_VIDEO = "VIDEO";
    String TYPE_AUDIO = "AUDIO";
    String TYPE_OTHER = "OTHER";

    String getId();

    String getTitle();

    /** {@link #TYPE_IMAGE}, {@link #TYPE_VIDEO}, etc. */
    String getType();

    /** Duration in milliseconds (0 for still images). */
    long getDurationMs();

    String getThumbUrl();

    String getOriginalMimeType();

    boolean isVideo();
}
