package com.example.android_apus.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.Auth.ApiClient;
import com.example.android_apus.Auth.ApiService;
import com.example.android_apus.Auth.SessionManager;
import com.example.android_apus.R;
import com.example.android_apus.location.GpsLocationTracker;
import com.example.android_apus.tracks.CoordinateDto;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;
import com.mapbox.maps.extension.style.layers.generated.CircleLayer;
import com.mapbox.maps.extension.style.layers.generated.LineLayer;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GpsRecordingActivity extends AppCompatActivity {

    private static final String USER_SOURCE_ID  = "user-location-source";
    private static final String USER_LAYER_ID   = "user-location-layer";
    private static final String TRACK_SOURCE_ID = "gps-track-source";
    private static final String TRACK_LAYER_ID  = "gps-track-layer";

    private TextView textActivityName;
    private TextView textTimer;
    private Button buttonPauseResume;
    private Button buttonEnd;
    private Button buttonCenter;
    private MapView mapView;
    private MapboxMap mapboxMap;

    private SessionManager sessionManager;
    private ApiService apiService;
    private GpsLocationTracker gpsTracker;

    // For the blue dot source
    private GeoJsonSource userLocationSource;

    // For centering behavior
    private boolean firstLocationSet = false;
    private boolean hasUserLocation = false;
    private double lastLat = 0.0;
    private double lastLon = 0.0;

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

        textActivityName  = findViewById(R.id.textGpsActivityName);
        textTimer         = findViewById(R.id.textGpsTimer);
        buttonPauseResume = findViewById(R.id.buttonGpsPauseResume);
        buttonEnd         = findViewById(R.id.buttonGpsEnd);
        buttonCenter      = findViewById(R.id.buttonGpsCenter);
        mapView           = findViewById(R.id.mapView);

        sessionManager = new SessionManager(this);
        apiService     = ApiClient.getClient().create(ApiService.class);

        mapboxMap = mapView.getMapboxMap();

        // Allow panning if parent is scrollable
        mapView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });

        // Shared GPS tracker
        gpsTracker = new GpsLocationTracker(
                this,
                (lat, lon) -> updateUserLocationOnMap(lat, lon)
        );

        activityTypeName = getIntent().getStringExtra("activityType");
        if (activityTypeName == null) activityTypeName = "Activity";
        textActivityName.setText(activityTypeName);

        routeFileName = getIntent().getStringExtra("routeFileName"); // may be null -> OK

        // Load style, then init blue dot and (optionally) route
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS, style -> {
            initUserLocationLayer(style);

            if (routeFileName != null && !routeFileName.isEmpty()) {
                loadTrackPoints(routeFileName);
            }
        });

        startTimeMillis = System.currentTimeMillis();
        handler.post(timerRunnable);

        buttonPauseResume.setOnClickListener(v -> onPauseResume());
        buttonEnd.setOnClickListener(v -> onEnd());
        buttonCenter.setOnClickListener(v -> centerOnUser());
    }

    // ----------------------------------------------------------
    // GPS + blue dot (first fix auto-center, then manual center button)
    // ----------------------------------------------------------

    private void initUserLocationLayer(Style style) {
        userLocationSource = new GeoJsonSource.Builder(USER_SOURCE_ID)
                .geometry(Point.fromLngLat(0, 0))
                .build();
        userLocationSource.bindTo(style);

        CircleLayer layer = new CircleLayer(USER_LAYER_ID, USER_SOURCE_ID);
        layer.circleRadius(6.0);
        layer.circleColor(0xFF0000FF);       // blue
        layer.circleOpacity(0.9);
        layer.circleStrokeWidth(2.0);
        layer.circleStrokeColor(0xFFFFFFFF); // white border
        layer.bindTo(style);
    }

    /**
     * Called from GpsLocationTracker on every location update.
     * Only auto-centers the first time; later updates just move the dot.
     */
    private void updateUserLocationOnMap(double lat, double lon) {
        if (mapboxMap == null) return;

        // store last known user location
        lastLat = lat;
        lastLon = lon;
        hasUserLocation = true;

        mapboxMap.getStyle(style -> {
            if (style == null) return;

            if (userLocationSource == null) {
                initUserLocationLayer(style);
            }

            if (userLocationSource != null) {
                userLocationSource.geometry(Point.fromLngLat(lon, lat));
            }

            // Auto-center only once, on the first valid location
            if (!firstLocationSet) {
                firstLocationSet = true;
                mapboxMap.setCamera(
                        new CameraOptions.Builder()
                                .center(Point.fromLngLat(lon, lat))
                                .zoom(15.0)
                                .build()
                );
            }
        });
    }

    /**
     * Called when the Center button is pressed.
     * Centers the camera on the last known user location (if available).
     */
    private void centerOnUser() {
        if (!hasUserLocation) {
            Toast.makeText(this,
                    "No location yet. Wait for GPS fix.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        double currentZoom = mapboxMap.getCameraState().getZoom();
        if (currentZoom <= 0) currentZoom = 15.0;

        mapboxMap.setCamera(
                new CameraOptions.Builder()
                        .center(Point.fromLngLat(lastLon, lastLat))
                        .zoom(currentZoom)
                        .build()
        );
    }

    // ----------------------------------------------------------
    // Route drawing (optional)
    // ----------------------------------------------------------

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

            clearTrack(style);

            List<Point> points = new ArrayList<>();
            for (CoordinateDto c : coords) {
                points.add(Point.fromLngLat(c.getLon(), c.getLat()));
            }

            GeoJsonSource source = new GeoJsonSource.Builder(TRACK_SOURCE_ID)
                    .geometry(LineString.fromLngLats(points))
                    .build();
            source.bindTo(style);

            LineLayer lineLayer = new LineLayer(TRACK_LAYER_ID, TRACK_SOURCE_ID);
            lineLayer.lineWidth(4.0);
            lineLayer.lineColor(Color.RED);
            lineLayer.bindTo(style);

            // No auto-centering here; camera is controlled by GPS first fix + Center button.
        });
    }

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

    // ----------------------------------------------------------
    // Timer / controls
    // ----------------------------------------------------------

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

    // ----------------------------------------------------------
    // Lifecycle + permission handling
    // ----------------------------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
        if (gpsTracker != null) {
            gpsTracker.start(); // will request permission or start updates
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) {
            mapView.onStop();
        }
        if (gpsTracker != null) {
            gpsTracker.stop();
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == GpsLocationTracker.REQUEST_LOCATION_PERMISSION) {
            boolean granted =
                    grantResults.length > 0
                            && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (granted && gpsTracker != null) {
                gpsTracker.start(); // start GPS after user granted permission
            } else {
                Toast.makeText(this,
                        "Location permission denied. Cannot show current position.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
