package com.example.android_apus.Auth;

import com.example.android_apus.tracks.CoordinateDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {

    // Adjust path to your real login endpoint: e.g. "api/Auth/login"
    @POST("api/Auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @GET("api/routing/tracks")
    Call<List<String>> getTrackNames(@Header("Authorization") String bearerToken);

    @GET("api/routing/tracks/{fileName}")
    Call<List<CoordinateDto>> getTrackPoints(
            @Header("Authorization") String bearerToken,
            @Path("fileName") String fileName
    );
}