package com.example.android_apus.tracks;

import com.google.gson.annotations.SerializedName;

public class CoordinateDto {
    @SerializedName("lat")
    private double lat;

    @SerializedName("lon")
    private double lon;

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }
}
