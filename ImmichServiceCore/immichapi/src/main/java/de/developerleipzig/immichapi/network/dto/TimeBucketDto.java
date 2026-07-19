package de.developerleipzig.immichapi.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * One entry from {@code GET /api/timeline/buckets}.
 */
public final class TimeBucketDto {
    @SerializedName("timeBucket")
    public String timeBucket;

    @SerializedName("count")
    public int count;
}
