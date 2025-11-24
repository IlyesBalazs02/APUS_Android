package com.example.android_apus.Auth;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    // Adjust path to your real login endpoint: e.g. "api/Auth/login"
    @POST("api/Auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);
}