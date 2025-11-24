package com.example.android_apus;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.Auth.ApiClient;
import com.example.android_apus.Auth.ApiService;
import com.example.android_apus.Auth.SessionManager;
import com.example.android_apus.tracks.CoordinateDto;
import com.example.android_apus.tracks.TracksActivity;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;
import com.mapbox.maps.extension.style.layers.generated.LineLayer;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;          // IMPORTANT: retrofit Call, not android.telecom.Call
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private MapView mapView;
    private MapboxMap mapboxMap;
    private ApiService apiService;
    private SessionManager sessionManager;

    private static final int REQUEST_TRACKS = 1001;
    private static final String TRACK_SOURCE_ID = "track-source";
    private static final String TRACK_LAYER_ID = "track-layer";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getClient().create(ApiService.class);

        mapboxMap = mapView.getMapboxMap();
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS, style -> {
            mapboxMap.setCamera(
                    new CameraOptions.Builder()
                            .center(Point.fromLngLat(19.0402, 47.4979)) // Budapest
                            .zoom(10.0)
                            .build()
            );
        });

        Button btnShowTracks = findViewById(R.id.btnShowTracks);
        btnShowTracks.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TracksActivity.class);
            startActivityForResult(intent, REQUEST_TRACKS);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_TRACKS && resultCode == RESULT_OK && data != null) {
            String fileName = data.getStringExtra("selectedTrack");
            if (fileName != null && !fileName.isEmpty()) {
                loadTrackPoints(fileName);
            }
        }
    }

    private void loadTrackPoints(String fileName) {
        String token = "Bearer " + sessionManager.getToken();

        apiService.getTrackPoints(token, fileName)
                .enqueue(new Callback<List<CoordinateDto>>() {
                    @Override
                    public void onResponse(Call<List<CoordinateDto>> call,
                                           Response<List<CoordinateDto>> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && !response.body().isEmpty()) {
                            drawTrack(response.body());
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "No points or error loading track",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<CoordinateDto>> call, Throwable t) {
                        Toast.makeText(MainActivity.this,
                                "Error: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Remove previous track layer + source if they exist.
     */
    private void clearTrack(Style style) {
        if (style.styleLayerExists(TRACK_LAYER_ID)) {
            style.removeStyleLayer(TRACK_LAYER_ID);
        }
        if (style.styleSourceExists(TRACK_SOURCE_ID)) {
            style.removeStyleSource(TRACK_SOURCE_ID);
        }
    }

    /**
     * Draw the selected track as a red line and move camera to its start.
     */
    private void drawTrack(List<CoordinateDto> coords) {
        mapboxMap.getStyle(style -> {
            if (style == null || coords == null || coords.isEmpty()) return;

            // 1) Clear previous track
            clearTrack(style);

            // 2) Convert to Mapbox Points
            List<Point> points = new ArrayList<>();
            for (CoordinateDto c : coords) {
                points.add(Point.fromLngLat(c.getLon(), c.getLat()));
            }

            // 3) Build GeoJsonSource with LineString geometry and bind it to the style
            GeoJsonSource source = new GeoJsonSource.Builder(TRACK_SOURCE_ID)
                    .geometry(LineString.fromLngLats(points))   // <-- use geometry, not data()
                    .build();
            source.bindTo(style);                               // <-- adds source to style

            // 4) Create LineLayer, style it, and bind to style
            LineLayer lineLayer = new LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID);
            lineLayer.lineWidth(4.0);
            lineLayer.lineColor(Color.RED);
            lineLayer.bindTo(style);                            // <-- adds layer to style

            // 5) Move camera to first point
            Point first = points.get(0);
            mapboxMap.setCamera(
                    new CameraOptions.Builder()
                            .center(first)
                            .zoom(14.0)
                            .build()
            );
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }
}
