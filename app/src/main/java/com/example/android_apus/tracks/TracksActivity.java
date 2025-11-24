package com.example.android_apus.tracks;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android_apus.Auth.ApiClient;
import com.example.android_apus.Auth.ApiService;
import com.example.android_apus.Auth.SessionManager;
import com.example.android_apus.R;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TracksActivity extends AppCompatActivity {

    private TracksAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracks);

        RecyclerView recyclerView = findViewById(R.id.recyclerTracks);
        adapter = new TracksAdapter();
        recyclerView.setAdapter(adapter);

        // Get token from your SessionManager (adjust method name if different)
        SessionManager sessionManager = new SessionManager(getApplicationContext());
        String token = sessionManager.getToken();  // or getToken(), etc.

        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "No auth token found", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<List<String>> call = apiService.getTrackNames("Bearer " + token);

        call.enqueue(new Callback<List<String>>() {
            @Override
            public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setTracks(response.body());
                } else {
                    Toast.makeText(TracksActivity.this, "Failed to load tracks", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<String>> call, Throwable t) {
                Toast.makeText(TracksActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
