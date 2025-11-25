package com.example.android_apus.tracks;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
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

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

        Button buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());


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

                    TracksAdapter adapter = new TracksAdapter(
                            names,
                            new TracksAdapter.OnTrackClickListener() {
                                @Override
                                public void onTrackClick(String fileName) {
                                    // old behaviour: pick track and return to MainActivity
                                    Intent result = new Intent();
                                    result.putExtra("selectedTrack", fileName);
                                    setResult(RESULT_OK, result);
                                    finish();
                                }

                                @Override
                                public void onDownloadClick(String fileName) {
                                    // NEW: only trigger backend mapsforge, no real download yet
                                    requestMapsforgeExport(fileName);
                                }
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

    // Stub: call backend to generate .map, but don't save file yet
    private void requestMapsforgeExport(String fileName) {
        String token = "Bearer " + sessionManager.getToken();
        MapsforgeTrackFileRequest request = new MapsforgeTrackFileRequest(fileName);

        Toast.makeText(this, "Requesting map export…", Toast.LENGTH_SHORT).show();

        apiService.exportMapsforge(token, request)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call,
                                           Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            // We do NOT read/save the body yet – just confirm
                            Toast.makeText(TracksActivity.this,
                                    "Map export OK (download not implemented yet)",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(TracksActivity.this,
                                    "Map export failed: " + response.code(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(TracksActivity.this,
                                "Map export error: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
