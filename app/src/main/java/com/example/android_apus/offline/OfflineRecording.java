package com.example.android_apus.offline;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.Auth.ActivityIdResponse;
import com.example.android_apus.Auth.ApiClient;
import com.example.android_apus.Auth.ApiService;
import com.example.android_apus.Auth.SessionManager;
import com.example.android_apus.R;
import com.example.android_apus.location.GpsLocationTracker;

import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OfflineRecording extends AppCompatActivity {

    public static final String EXTRA_ROUTE_NAME     = "routeName";
    public static final String EXTRA_ACTIVITY_TYPE  = "activityType"; // e.g. "Running"

    // SharedPreferences keys for pending GPS uploads
    private static final String PREFS_PENDING       = "offline_pending_uploads";
    private static final String KEY_PENDING_GPX     = "pending_gpx_path";
    private static final String KEY_PENDING_TYPE    = "pending_activity_type";

    private MapView mapView;
    private TileRendererLayer tileLayer;
    private Polyline routeLine;

    private TextView textActivityName;
    private TextView textTimer;
    private Button buttonPauseResume;
    private Button buttonEnd;
    private Button buttonCenter;

    private SessionManager sessionManager;
    private ApiService.AndroidActivityApi gpsApi;

    // GPS + user marker
    private GpsLocationTracker gpsTracker;
    private Circle userCircle;
    private boolean firstFixSet = false;
    private double lastLat = 0.0;
    private double lastLon = 0.0;

    // Timer
    private boolean isRunning = true;
    private long startTimeMillis;
    private long pausedAccumulated = 0L;
    private long lastPauseStart = 0L;

    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                long now = System.currentTimeMillis();
                long elapsed = (now - startTimeMillis) - pausedAccumulated;
                textTimer.setText(formatDuration(elapsed));
            }
            handler.postDelayed(this, 1000);
        }
    };

    private String routeName;
    private String activityTypeName;

    // Recorded GPS samples
    private static class Sample {
        final double lat;
        final double lon;
        final long timeMillis;

        Sample(double lat, double lon, long timeMillis) {
            this.lat = lat;
            this.lon = lon;
            this.timeMillis = timeMillis;
        }
    }

    private final List<Sample> samples = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidGraphicFactory.createInstance(getApplication());
        setContentView(R.layout.activity_offline_recording);

        mapView          = findViewById(R.id.mapView);
        textActivityName = findViewById(R.id.textOfflineActivityName);
        textTimer        = findViewById(R.id.textOfflineTimer);
        buttonPauseResume = findViewById(R.id.buttonOfflinePauseResume);
        buttonEnd        = findViewById(R.id.buttonOfflineEnd);
        buttonCenter     = findViewById(R.id.buttonOfflineCenter);

        sessionManager = new SessionManager(this);
        gpsApi = ApiClient.getClient().create(ApiService.AndroidActivityApi.class);

        routeName = getIntent().getStringExtra(EXTRA_ROUTE_NAME);
        activityTypeName = getIntent().getStringExtra(EXTRA_ACTIVITY_TYPE);
        if (activityTypeName == null) activityTypeName = "Activity";

        textActivityName.setText(activityTypeName);

        if (routeName == null || routeName.trim().isEmpty()) {
            Toast.makeText(this, "No route selected.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Basic map config
        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.getModel().mapViewPosition.setZoomLevel((byte) 12);

        // Try to flush any previously pending offline GPS uploads
        tryUploadPendingIfAny();

        File rootDir = OfflineRouteStore.rootDir(this);
        File mapFile = new File(rootDir, routeName + ".map");
        File gpxFile = new File(rootDir, routeName + ".gpx");

        if (!mapFile.exists()) {
            Toast.makeText(this, "Missing map file: " + mapFile.getName(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            loadOfflineMap(mapFile);

            if (gpxFile.exists()) {
                List<LatLong> pts = parseGpx(gpxFile);
                drawRoute(pts);
                if (!pts.isEmpty()) {
                    mapView.getModel().mapViewPosition.setCenter(pts.get(0));
                }
            } else {
                Toast.makeText(this, "No GPX found for this route.", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e("OfflineRecording", "Failed to load offline route", e);
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // GPS tracker – we want to see user on map and record samples
        gpsTracker = new GpsLocationTracker(
                this,
                (lat, lon) -> onLocationUpdate(lat, lon)
        );

        // Timer start
        startTimeMillis = System.currentTimeMillis();
        handler.post(timerRunnable);

        buttonPauseResume.setOnClickListener(v -> onPauseResume());
        buttonEnd.setOnClickListener(v -> onEnd());
        buttonCenter.setOnClickListener(v -> centerOnUser());
    }

    // ---------------------- GPS + user marker ----------------------

    private void onLocationUpdate(double lat, double lon) {
        lastLat = lat;
        lastLon = lon;

        // Record sample only while running
        if (isRunning) {
            samples.add(new Sample(lat, lon, System.currentTimeMillis()));
        }

        LatLong ll = new LatLong(lat, lon);

        if (userCircle == null) {
            Paint fill = AndroidGraphicFactory.INSTANCE.createPaint();
            fill.setStyle(Style.FILL);
            fill.setColor(0x800000FF); // semi-transparent blue

            Paint stroke = AndroidGraphicFactory.INSTANCE.createPaint();
            stroke.setStyle(Style.STROKE);
            stroke.setStrokeWidth(2);
            stroke.setColor(0xFFFFFFFF); // white border

            // Radius in meters (approx) – Mapsforge interprets as meters.
            userCircle = new Circle(ll, 10, fill, stroke);
            mapView.getLayerManager().getLayers().add(userCircle);
        } else {
            userCircle.setLatLong(ll);
        }

        // Auto-center only on first fix
        if (!firstFixSet) {
            firstFixSet = true;
            mapView.getModel().mapViewPosition.setCenter(ll);
            mapView.getModel().mapViewPosition.setZoomLevel((byte) 15);
        }

        mapView.repaint();
    }

    private void centerOnUser() {
        if (!firstFixSet) {
            Toast.makeText(this, "No GPS fix yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        LatLong ll = new LatLong(lastLat, lastLon);
        mapView.getModel().mapViewPosition.setCenter(ll);
        mapView.repaint();
    }

    // ---------------------- Map drawing (route) ----------------------

    private void loadOfflineMap(File mapFile) {
        MapFile mf = new MapFile(mapFile);

        tileLayer = new TileRendererLayer(
                AndroidUtil.createTileCache(
                        this,
                        "offlineRecordingCache",
                        mapView.getModel().displayModel.getTileSize(),
                        1f,
                        mapView.getModel().frameBufferModel.getOverdrawFactor()
                ),
                mf,
                mapView.getModel().mapViewPosition,
                AndroidGraphicFactory.INSTANCE
        );

        tileLayer.setXmlRenderTheme(InternalRenderTheme.DEFAULT);
        mapView.getLayerManager().getLayers().add(tileLayer);
        mapView.repaint();
    }

    private void drawRoute(List<LatLong> pts) {
        if (pts == null || pts.size() < 2) return;

        Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(6);

        routeLine = new Polyline(paint, AndroidGraphicFactory.INSTANCE);
        routeLine.getLatLongs().addAll(pts);

        mapView.getLayerManager().getLayers().add(routeLine);
        mapView.repaint();
    }

    private List<LatLong> parseGpx(File gpxFile) throws Exception {
        List<LatLong> out = new ArrayList<>();

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();

        try (FileInputStream fis = new FileInputStream(gpxFile)) {
            xpp.setInput(fis, "UTF-8");
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "trkpt".equals(xpp.getName())) {
                    String latS = xpp.getAttributeValue(null, "lat");
                    String lonS = xpp.getAttributeValue(null, "lon");
                    if (latS != null && lonS != null) {
                        out.add(new LatLong(Double.parseDouble(latS), Double.parseDouble(lonS)));
                    }
                }
                eventType = xpp.next();
            }
        }
        return out;
    }

    // ---------------------- Timer controls ----------------------

    private void onPauseResume() {
        if (isRunning) {
            isRunning = false;
            buttonPauseResume.setText("Resume");
            lastPauseStart = System.currentTimeMillis();
        } else {
            isRunning = true;
            buttonPauseResume.setText("Pause");
            long now = System.currentTimeMillis();
            pausedAccumulated += (now - lastPauseStart);
        }
    }

    private void onEnd() {
        long now = System.currentTimeMillis();
        long elapsedMillis = (now - startTimeMillis) - pausedAccumulated;

        if (elapsedMillis <= 0) {
            Toast.makeText(this, "Duration too short", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (samples.isEmpty()) {
            // No GPS data: nothing to upload as GPX.
            Toast.makeText(this,
                    "No GPS samples recorded, nothing to upload.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Write GPX to a file under offline_routes root
        File rootDir = OfflineRouteStore.rootDir(this);
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date(startTimeMillis));
        File recordedGpx = new File(rootDir,
                routeName + "_recorded_" + timestamp + ".gpx");

        try {
            writeGpx(recordedGpx, samples);
        } catch (Exception e) {
            Log.e("OfflineRecording", "Failed to write GPX", e);
            Toast.makeText(this,
                    "Failed to write GPX: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Try to upload immediately if online + logged in
        String token = sessionManager.getToken();
        if (token != null && !token.isEmpty() && isOnline()) {
            uploadGpsNow(recordedGpx, activityTypeName, token);
        } else {
            // Store pending info – will be retried next time in onCreate
            savePending(recordedGpx.getAbsolutePath(), activityTypeName);
            Toast.makeText(this,
                    "No internet or not logged in. Will upload next time when online.",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    // ---------------------- GPX writing ----------------------

    private void writeGpx(File file, List<Sample> samples) throws Exception {
        if (samples == null || samples.isEmpty()) return;

        // Very simple GPX 1.1
        try (FileWriter fw = new FileWriter(file, false)) {
            fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            fw.write("<gpx version=\"1.1\" creator=\"APUS Android\" ");
            fw.write("xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
            fw.write("  <trk>\n");
            fw.write("    <name>" + activityTypeName + "</name>\n");
            fw.write("    <trkseg>\n");

            SimpleDateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            iso8601.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

            for (Sample s : samples) {
                fw.write(String.format(Locale.US,
                        "      <trkpt lat=\"%f\" lon=\"%f\">\n",
                        s.lat, s.lon));
                fw.write("        <time>" + iso8601.format(new Date(s.timeMillis)) + "</time>\n");
                fw.write("      </trkpt>\n");
            }

            fw.write("    </trkseg>\n");
            fw.write("  </trk>\n");
            fw.write("</gpx>\n");
        }
    }

    // ---------------------- Pending upload handling ----------------------

    private void savePending(String gpxPath, String activityType) {
        getSharedPreferences(PREFS_PENDING, MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_GPX, gpxPath)
                .putString(KEY_PENDING_TYPE, activityType)
                .apply();
    }

    private void clearPending() {
        getSharedPreferences(PREFS_PENDING, MODE_PRIVATE)
                .edit()
                .remove(KEY_PENDING_GPX)
                .remove(KEY_PENDING_TYPE)
                .apply();
    }

    private void tryUploadPendingIfAny() {
        String token = sessionManager.getToken();
        if (token == null || token.isEmpty()) return;
        if (!isOnline()) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_PENDING, MODE_PRIVATE);
        String path = prefs.getString(KEY_PENDING_GPX, null);
        String type = prefs.getString(KEY_PENDING_TYPE, null);

        if (path == null || type == null) return;

        File f = new File(path);
        if (!f.exists()) {
            clearPending();
            return;
        }

        uploadGpsNow(f, type, token);
    }

    private void uploadGpsNow(File gpxFile, String activityType, String token) {
        String bearer = "Bearer " + token;

        RequestBody fileBody = RequestBody.create(
                MediaType.parse("application/gpx+xml"),
                gpxFile
        );

        MultipartBody.Part trackPart = MultipartBody.Part.createFormData(
                "trackFile",
                gpxFile.getName(),
                fileBody
        );

        RequestBody typeBody = RequestBody.create(
                MediaType.parse("text/plain"),
                activityType
        );

        gpsApi.uploadGpsActivity(bearer, trackPart, typeBody)
                .enqueue(new retrofit2.Callback<Void>() {
                    @Override
                    public void onResponse(retrofit2.Call<Void> call,
                                           retrofit2.Response<Void> response) {
                        if (response.isSuccessful()) {
                            clearPending();
                            Toast.makeText(OfflineRecording.this,
                                    "Offline GPS activity uploaded.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            // keep pending
                            Toast.makeText(OfflineRecording.this,
                                    "Upload failed: " + response.code(),
                                    Toast.LENGTH_LONG).show();
                        }
                        finish();
                    }

                    @Override
                    public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                        // keep pending
                        Toast.makeText(OfflineRecording.this,
                                "Upload error: " + t.getMessage(),
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }


    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    // ---------------------- Lifecycle ----------------------

    @Override
    protected void onResume() {
        super.onResume();
        if (gpsTracker != null) {
            gpsTracker.start();   // start requesting locations
        }
    }

    @Override
    protected void onPause() {
        if (gpsTracker != null) {
            gpsTracker.stop();    // stop location updates
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);

        if (mapView != null) {
            mapView.destroyAll(); // Mapsforge cleanup
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @androidx.annotation.NonNull String[] permissions,
                                           @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == GpsLocationTracker.REQUEST_LOCATION_PERMISSION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (granted && gpsTracker != null) {
                gpsTracker.start();
            } else {
                Toast.makeText(this,
                        "Location permission denied. Cannot record GPS.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
