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

import java.io.File;
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
                                    requestRouteGpx(fileName);
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

    private File getOfflineRoutesDir() {
        File dir = new File(getExternalFilesDir(null), "offline_routes");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void requestRouteGpx(String fileName) {
        String token = "Bearer " + sessionManager.getToken();
        MapsforgeTrackFileRequest request = new MapsforgeTrackFileRequest(fileName);

        apiService.downloadRouteGpx(token, request)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(TracksActivity.this,
                                    "GPX download failed: " + response.code(),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        try {
                            File outFile = new File(getOfflineRoutesDir(), sanitize(fileName) + ".gpx");
                            saveResponseBodyToFile(response.body(), outFile);

                            String msg = "Saved GPX: " + outFile.getAbsolutePath();
                            android.util.Log.i("OfflineRoutes", msg);
                            Toast.makeText(TracksActivity.this, msg, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            android.util.Log.e("OfflineRoutes", "GPX save failed", e);
                            Toast.makeText(TracksActivity.this,
                                    "GPX save failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(TracksActivity.this,
                                "GPX error: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
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
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(TracksActivity.this,
                                    "Map export failed: " + response.code(),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        try {
                            File outFile = new File(getOfflineRoutesDir(), sanitize(fileName) + ".map");
                            saveResponseBodyToFile(response.body(), outFile);

                            String msg = "Saved: " + outFile.getAbsolutePath();
                            android.util.Log.i("OfflineRoutes", msg);
                            Toast.makeText(TracksActivity.this, msg, Toast.LENGTH_LONG).show();

                            // Optional: download GPX as well (see section 3)
                            // requestRouteGpx(fileName);

                        } catch (Exception e) {
                            android.util.Log.e("OfflineRoutes", "Save failed", e);
                            Toast.makeText(TracksActivity.this,
                                    "Save failed: " + e.getMessage(),
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

    private void saveResponseBodyToFile(ResponseBody body, File outFile) throws Exception {
        try (java.io.InputStream is = body.byteStream();
             java.io.OutputStream os = new java.io.FileOutputStream(outFile)) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }
    }

    private String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9áéíóöőúüűÁÉÍÓÖŐÚÜŰ _-]", "_").trim();
    }

}
