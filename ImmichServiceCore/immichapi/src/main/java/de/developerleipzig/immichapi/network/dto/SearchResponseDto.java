package de.developerleipzig.immichapi.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class SearchResponseDto {
    @SerializedName("assets")
    public SearchAssetResponseDto assets;

    public static final class SearchAssetResponseDto {
        @SerializedName("total")
        public int total;

        @SerializedName("count")
        public int count;

        @SerializedName("items")
        public List<AssetResponseDto> items;

        @SerializedName("nextPage")
        public String nextPage;
    }
}
