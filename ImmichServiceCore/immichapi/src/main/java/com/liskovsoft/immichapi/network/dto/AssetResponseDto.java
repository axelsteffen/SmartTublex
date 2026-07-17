package com.liskovsoft.immichapi.network.dto;

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

    @SerializedName("localDateTime")
    public String localDateTime;
}
