package de.developerleipzig.immichapi.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class MetadataSearchDto {
    @SerializedName("albumIds")
    public List<String> albumIds;

    @SerializedName("type")
    public String type;

    /** Immich page is 1-based. */
    @SerializedName("page")
    public Integer page;

    @SerializedName("size")
    public Integer size;

    @SerializedName("order")
    public String order;

    @SerializedName("withExif")
    public Boolean withExif;

    /** Inclusive lower bound (ISO-8601), e.g. {@code 2024-01-01T00:00:00.000Z}. */
    @SerializedName("takenAfter")
    public String takenAfter;

    /** Exclusive upper bound (ISO-8601). */
    @SerializedName("takenBefore")
    public String takenBefore;
}
