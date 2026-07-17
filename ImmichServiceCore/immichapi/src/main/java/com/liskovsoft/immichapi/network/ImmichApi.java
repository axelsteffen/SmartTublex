package com.liskovsoft.immichapi.network;

import com.liskovsoft.immichapi.network.dto.AlbumResponseDto;
import com.liskovsoft.immichapi.network.dto.AssetResponseDto;
import com.liskovsoft.immichapi.network.dto.MetadataSearchDto;
import com.liskovsoft.immichapi.network.dto.SearchResponseDto;
import com.liskovsoft.immichapi.network.dto.UserResponseDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Immich REST endpoints used by the MVP (relative to {@code /api/}).
 */
public interface ImmichApi {
    @GET("users/me")
    Call<UserResponseDto> getMyUser();

    /** Validate with an explicit key (before prefs are trusted). */
    @GET("users/me")
    Call<UserResponseDto> getMyUser(@Header("x-api-key") String apiKey);

    @GET("albums")
    Call<List<AlbumResponseDto>> getAllAlbums();

    @GET("assets/{id}")
    Call<AssetResponseDto> getAsset(@Path("id") String id);

    @POST("search/metadata")
    Call<SearchResponseDto> searchMetadata(@Body MetadataSearchDto body);
}
