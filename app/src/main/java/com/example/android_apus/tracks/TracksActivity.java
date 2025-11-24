package com.example.android_apus.tracks;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android_apus.Auth.ApiClient;
import com.example.android_apus.Auth.ApiService;
import com.example.android_apus.Auth.SessionManager;
import com.example.android_apus.R;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// TracksActivity.java
public class TracksActivity extends AppCompatActivity {

    private RecyclerView recyclerTracks;
    private ApiService apiService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracks);

        recyclerTracks = findViewById(R.id.recyclerTracks);
        recyclerTracks.setLayoutManager(new LinearLayoutManager(this));

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getClient().create(ApiService.class);

        loadTrackNames();
    }

    private void loadTrackNames() {
        String token = "Bearer " + sessionManager.getToken();

        apiService.getTrackNames(token).enqueue(new Callback<List<String>>() {
            @Override
            public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<String> names = response.body();
                    TracksAdapter adapter = new TracksAdapter(names, fileName -> {
                        Intent result = new Intent();
                        result.putExtra("selectedTrack", fileName);
                        setResult(RESULT_OK, result);
                        finish();
                    });
                    recyclerTracks.setAdapter(adapter);
                } else {
                    Toast.makeText(TracksActivity.this,
                            "Failed to load track names", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<String>> call, Throwable t) {
                Toast.makeText(TracksActivity.this,
                        "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}

