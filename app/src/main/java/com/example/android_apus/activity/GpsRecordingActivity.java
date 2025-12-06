package com.example.android_apus.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.Auth.ApiClient;
import com.example.android_apus.Auth.ApiService;
import com.example.android_apus.Auth.SessionManager;
import com.example.android_apus.R;
import com.example.android_apus.tracks.CoordinateDto;
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

import retrofit2.Call;      // retrofit2.Call, same as in MainActivity
import retrofit2.Callback;
import retrofit2.Response;

public class GpsRecordingActivity extends AppCompatActivity {

    private TextView textActivityName;
    private TextView textTimer;
    private Button buttonPauseResume;
    private Button buttonEnd;

    private MapView mapView;
    private MapboxMap mapboxMap;

    private ApiService apiService;
    private SessionManager sessionManager;

    private static final String TRACK_SOURCE_ID = "gps-track-source";
    private static final String TRACK_LAYER_ID = "gps-track-layer";

    private boolean isRunning = true;
    private long startTimeMillis;
    private long pausedTimeAccumulated = 0L;
    private long lastPauseStart = 0L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                long now = System.currentTimeMillis();
                long elapsed = (now - startTimeMillis) - pausedTimeAccumulated;
                textTimer.setText(formatDuration(elapsed));
            }
            handler.postDelayed(this, 1000);
        }
    };

    private String activityTypeName;
    private String routeFileName; // may be null

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gps_recording);

        textActivityName = findViewById(R.id.textGpsActivityName);
        textTimer = findViewById(R.id.textGpsTimer);
        buttonPauseResume = findViewById(R.id.buttonGpsPauseResume);
        buttonEnd = findViewById(R.id.buttonGpsEnd);
        mapView = findViewById(R.id.mapView);

        // Allow panning – prevent parent from stealing drag gestures
        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false; // let Mapbox handle the gesture itself
        });

        // Auth + API setup (same pattern as MainActivity)
        sessionManager = new SessionManager(this);
        apiService = ApiClient.getClient().create(ApiService.class);

        activityTypeName = getIntent().getStringExtra("activityType");
        if (activityTypeName == null) {
            activityTypeName = "Activity";
        }
        textActivityName.setText(activityTypeName);

        routeFileName = getIntent().getStringExtra("routeFileName");

        mapboxMap = mapView.getMapboxMap();
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS, style -> {
            // You can set an initial camera here if you want
            // but we will recenter to the route after loading it.

            if (routeFileName != null && !routeFileName.isEmpty()) {
                loadTrackPoints(routeFileName);
            }
        });

        startTimeMillis = System.currentTimeMillis();
        handler.post(timerRunnable);

        buttonPauseResume.setOnClickListener(v -> onPauseResume());
        buttonEnd.setOnClickListener(v -> onEnd());
    }

    // === Track loading & drawing (same idea as MainActivity) ===

    private void loadTrackPoints(String fileName) {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "No auth token, cannot load route", Toast.LENGTH_SHORT).show();
            return;
        }

        String bearer = "Bearer " + token;

        apiService.getTrackPoints(bearer, fileName)
                .enqueue(new Callback<List<CoordinateDto>>() {
                    @Override
                    public void onResponse(Call<List<CoordinateDto>> call,
                                           Response<List<CoordinateDto>> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && !response.body().isEmpty()) {
                            drawTrack(response.body());
                        } else {
                            Toast.makeText(GpsRecordingActivity.this,
                                    "No points or error loading track",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<CoordinateDto>> call, Throwable t) {
                        Toast.makeText(GpsRecordingActivity.this,
                                "Error: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void clearTrack(Style style) {
        if (style.styleLayerExists(TRACK_LAYER_ID)) {
            style.removeStyleLayer(TRACK_LAYER_ID);
        }
        if (style.styleSourceExists(TRACK_SOURCE_ID)) {
            style.removeStyleSource(TRACK_SOURCE_ID);
        }
    }

    private void drawTrack(List<CoordinateDto> coords) {
        mapboxMap.getStyle(style -> {
            if (style == null || coords == null || coords.isEmpty()) return;

            // 1) Clear previous line if any
            clearTrack(style);

            // 2) Convert to Mapbox Points (lon, lat)
            List<Point> points = new ArrayList<>();
            for (CoordinateDto c : coords) {
                points.add(Point.fromLngLat(c.getLon(), c.getLat()));
            }

            // 3) Create GeoJsonSource with LineString geometry
            GeoJsonSource source = new GeoJsonSource.Builder(TRACK_SOURCE_ID)
                    .geometry(LineString.fromLngLats(points))
                    .build();
            source.bindTo(style);

            // 4) Create LineLayer referencing the source
            LineLayer lineLayer = new LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID);
            lineLayer.lineWidth(4.0);
            lineLayer.lineColor(Color.RED);
            lineLayer.bindTo(style);

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

    // === Timer / buttons ===

    private void onPauseResume() {
        if (isRunning) {
            isRunning = false;
            buttonPauseResume.setText("Resume");
            lastPauseStart = System.currentTimeMillis();
        } else {
            isRunning = true;
            buttonPauseResume.setText("Pause");
            long now = System.currentTimeMillis();
            pausedTimeAccumulated += (now - lastPauseStart);
        }
    }

    private void onEnd() {
        long now = System.currentTimeMillis();
        long elapsed = (now - startTimeMillis) - pausedTimeAccumulated;

        // TODO: stop GPS tracking, build DTO, upload to server
        Toast.makeText(this,
                "GPS activity finished, duration: " + formatDuration(elapsed),
                Toast.LENGTH_LONG).show();

        finish();
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    // === MapView lifecycle ===

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) {
            mapView.onStop();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);
        if (mapView != null) {
            mapView.onDestroy();
        }
    }
}
