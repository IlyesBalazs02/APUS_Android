package com.example.android_apus.Auth;

import com.example.android_apus.activity.NonGpsActivityUploadRequest;
import com.example.android_apus.tracks.CoordinateDto;
import com.example.android_apus.tracks.MapsforgeExportRequest;
import com.example.android_apus.tracks.MapsforgeTrackFileRequest;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Streaming;

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

    @POST("api/mapsforge/from-track-file")
    Call<ResponseBody> exportMapsforge(
            @Header("Authorization") String bearerToken,
            @Body MapsforgeTrackFileRequest request
    );

    @POST("api/android/activities/nongps")
    Call<Void> uploadNonGpsActivity(
            @Header("Authorization") String bearerToken,
            @Body NonGpsActivityUploadRequest request
    );
}