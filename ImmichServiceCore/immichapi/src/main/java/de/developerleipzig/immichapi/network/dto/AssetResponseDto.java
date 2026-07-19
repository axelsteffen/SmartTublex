package de.developerleipzig.immichapi.network.dto;

import com.google.gson.annotations.SerializedName;

public final class AssetResponseDto {
    @SerializedName("id")
    public String id;

    @SerializedName("originalFileName")
    public String originalFileName;

    @SerializedName("type")
    public String type;

    /** Duration in milliseconds (Immich OpenAPI). */
    @SerializedName("duration")
    public Long duration;

    @SerializedName("originalMimeType")
    public String originalMimeType;

    /**
     * Legacy Immich field (removed from current AssetResponseDto OpenAPI).
     * Still parsed when present on older servers.
     */
    @SerializedName("encodedVideoPath")
    public String encodedVideoPath;

    @SerializedName("localDateTime")
    public String localDateTime;
}
