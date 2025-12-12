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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

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
    private GeoJsonSource userLocationSource;
    private boolean firstLocationSet = false;
    private boolean hasUserLocation = false;
    private double lastLat = 0.0;
    private double lastLon = 0.0;

    private boolean isRunning = true;
    private long startTimeMillis;
    private long pausedTimeAccumulated = 0L;
    private long lastPauseStart = 0L;

    private static final double OFF_TRAIL_DISTANCE_M = 35.0;
    private static final double ON_TRAIL_DISTANCE_M  = 25.0;
    private static final int OFF_TRAIL_CONSECUTIVE_FIXES = 3;
    private static final long OFF_TRAIL_WARNING_COOLDOWN_MS = 30_000L;

    private volatile double[] selectedTrackLats = null;
    private volatile double[] selectedTrackLons = null;

    private int offTrailFixes = 0;
    private boolean isOffTrail = false;
    private long lastOffTrailWarningAtElapsed = 0L;


    private final List<RecordedPoint> recordedPoints = new ArrayList<>();

    private static class RecordedPoint {
        final double lat;
        final double lon;
        final long timeMillis;

        RecordedPoint(double lat, double lon, long timeMillis) {
            this.lat = lat;
            this.lon = lon;
            this.timeMillis = timeMillis;
        }
    }


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
    private String routeFileName;

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
                (lat, lon) -> {
                    updateUserLocationOnMap(lat, lon);

                    if (isRunning) {
                        recordedPoints.add(new RecordedPoint(
                                lat,
                                lon,
                                System.currentTimeMillis()
                        ));
                        maybeWarnOffTrail(lat, lon);
                    }
                }
        );


        activityTypeName = getIntent().getStringExtra("activityType");
        if (activityTypeName == null) activityTypeName = "Activity";
        textActivityName.setText(activityTypeName);

        routeFileName = getIntent().getStringExtra("routeFileName");

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

    // GPS + blue dot

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

    // Route drawing
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
                            setSelectedTrackForOffTrailWarning(response.body());
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


    private void setSelectedTrackForOffTrailWarning(List<CoordinateDto> coords) {
        if (coords == null || coords.size() < 2) {
            selectedTrackLats = null;
            selectedTrackLons = null;
            resetOffTrailState();
            return;
        }

        double[] lats = new double[coords.size()];
        double[] lons = new double[coords.size()];
        for (int i = 0; i < coords.size(); i++) {
            CoordinateDto c = coords.get(i);
            lats[i] = c.getLat();
            lons[i] = c.getLon();
        }

        selectedTrackLats = lats;
        selectedTrackLons = lons;
        resetOffTrailState();
    }

    private void resetOffTrailState() {
        offTrailFixes = 0;
        isOffTrail = false;
        lastOffTrailWarningAtElapsed = 0L;
    }

    private void maybeWarnOffTrail(double lat, double lon) {
        double[] lats = selectedTrackLats;
        double[] lons = selectedTrackLons;
        if (lats == null || lons == null || lats.length < 2 || lons.length < 2) return;

        double distanceM = distanceToPolylineMeters(lat, lon, lats, lons);

        if (distanceM >= OFF_TRAIL_DISTANCE_M) {
            offTrailFixes++;
        } else if (distanceM <= ON_TRAIL_DISTANCE_M) {
            offTrailFixes = 0;
            isOffTrail = false;
        }

        if (offTrailFixes < OFF_TRAIL_CONSECUTIVE_FIXES) return;
        isOffTrail = true;

        long nowElapsed = android.os.SystemClock.elapsedRealtime();
        if (lastOffTrailWarningAtElapsed != 0L
                && (nowElapsed - lastOffTrailWarningAtElapsed) < OFF_TRAIL_WARNING_COOLDOWN_MS) {
            return;
        }

        lastOffTrailWarningAtElapsed = nowElapsed;

        String msg = String.format(java.util.Locale.US,
                "Off trail: %.0f m from the selected route",
                distanceM);
        runOnUiThread(() -> Toast.makeText(GpsRecordingActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    private static double distanceToPolylineMeters(double lat, double lon, double[] lats, double[] lons) {
        final double refLatRad = Math.toRadians(lat);
        final double r = 6371000.0;

        final double px = Math.toRadians(lon) * Math.cos(refLatRad) * r;
        final double py = Math.toRadians(lat) * r;

        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < lats.length - 1; i++) {
            double ax = Math.toRadians(lons[i]) * Math.cos(refLatRad) * r;
            double ay = Math.toRadians(lats[i]) * r;
            double bx = Math.toRadians(lons[i + 1]) * Math.cos(refLatRad) * r;
            double by = Math.toRadians(lats[i + 1]) * r;
            double d = distancePointToSegment(px, py, ax, ay, bx, by);
            if (d < best) best = d;
        }
        return best;
    }

    private static double distancePointToSegment(double px, double py,
                                                 double ax, double ay,
                                                 double bx, double by) {
        double vx = bx - ax;
        double vy = by - ay;
        double wx = px - ax;
        double wy = py - ay;

        double len2 = vx * vx + vy * vy;
        if (len2 <= 0.0) {
            double dx = px - ax;
            double dy = py - ay;
            return Math.sqrt(dx * dx + dy * dy);
        }

        double t = (wx * vx + wy * vy) / len2;
        if (t < 0.0) t = 0.0;
        else if (t > 1.0) t = 1.0;

        double cx = ax + t * vx;
        double cy = ay + t * vy;
        double dx = px - cx;
        double dy = py - cy;
        return Math.sqrt(dx * dx + dy * dy);
    }


    // Timer

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

        isRunning = false;

        if (gpsTracker != null) {
            gpsTracker.stop();
        }

        if (recordedPoints.size() < 2) {
            Toast.makeText(this,
                    "Not enough GPS points recorded – nothing to upload.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            File gpxFile = writeGpxFile();
            uploadGpxFile(gpxFile);
        } catch (IOException e) {
            Toast.makeText(this,
                    "Could not create GPX file: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private File writeGpxFile() throws IOException {
        File dir = new File(getCacheDir(), "recorded_gpx");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create GPX cache directory");
        }

        String fileName = "activity_" + System.currentTimeMillis() + ".gpx";
        File gpxFile = new File(dir, fileName);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"APUS Android\" ")
                .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("  <trk>\n");
        sb.append("    <name>")
                .append(activityTypeName)
                .append("</name>\n");
        sb.append("    <trkseg>\n");

        for (RecordedPoint p : recordedPoints) {
            sb.append("      <trkpt lat=\"")
                    .append(p.lat)
                    .append("\" lon=\"")
                    .append(p.lon)
                    .append("\">\n");

            String isoTime = Instant.ofEpochMilli(p.timeMillis).toString();
            sb.append("        <time>")
                    .append(isoTime)
                    .append("</time>\n");

            sb.append("      </trkpt>\n");
        }

        sb.append("    </trkseg>\n");
        sb.append("  </trk>\n");
        sb.append("</gpx>\n");

        try (FileOutputStream fos = new FileOutputStream(gpxFile)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }

        return gpxFile;
    }

    private void uploadGpxFile(File gpxFile) {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this,
                    "You are not logged in – cannot upload activity.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        ApiService.AndroidActivityApi androidActivityApi =
                ApiClient.getClient().create(ApiService.AndroidActivityApi.class);

        RequestBody fileBody = RequestBody.create(
                MediaType.parse("application/gpx+xml"),
                gpxFile
        );

        MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "trackFile",
                gpxFile.getName(),
                fileBody
        );

        RequestBody activityTypePart = RequestBody.create(
                MediaType.parse("text/plain"),
                activityTypeName != null ? activityTypeName : ""
        );

        androidActivityApi.uploadGpsActivity(
                        "Bearer " + token,
                        filePart,
                        activityTypePart
                )
                .enqueue(new retrofit2.Callback<Void>() {
                    @Override
                    public void onResponse(retrofit2.Call<Void> call,
                                           retrofit2.Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(GpsRecordingActivity.this,
                                    "GPS activity uploaded",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(GpsRecordingActivity.this,
                                    "Upload failed: " + response.code(),
                                    Toast.LENGTH_LONG).show();
                        }
                        finish();
                    }

                    @Override
                    public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                        Toast.makeText(GpsRecordingActivity.this,
                                "Upload error: " + t.getMessage(),
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }



    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    // Lifecycle

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) {
            mapView.onStart();
        }
        if (gpsTracker != null) {
            gpsTracker.start();
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
                gpsTracker.start();
            } else {
                Toast.makeText(this,
                        "Location permission denied. Cannot show current position.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
