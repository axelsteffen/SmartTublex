package com.liskovsoft.immichapi.network.dto;

import com.google.gson.annotations.SerializedName;

public final class UserResponseDto {
    @SerializedName("id")
    public String id;

    @SerializedName("name")
    public String name;

    @SerializedName("email")
    public String email;
}
