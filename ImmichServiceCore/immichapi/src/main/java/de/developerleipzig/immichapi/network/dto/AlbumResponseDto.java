package de.developerleipzig.immichapi.network.dto;

import com.google.gson.annotations.SerializedName;

public final class AlbumResponseDto {
    @SerializedName("id")
    public String id;

    @SerializedName("albumName")
    public String albumName;

    @SerializedName("assetCount")
    public int assetCount;

    @SerializedName("albumThumbnailAssetId")
    public String albumThumbnailAssetId;

    @SerializedName("description")
    public String description;
}
