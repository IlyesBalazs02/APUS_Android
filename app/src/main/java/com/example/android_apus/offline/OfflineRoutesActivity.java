package com.example.android_apus.offline;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android_apus.R;
import com.example.android_apus.activity.ActivityType;

import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class OfflineRoutesActivity extends AppCompatActivity {

    private Spinner spinnerActivityType;
    private MapView mapView;
    private TextView selectedText;
    private Button buttonStart;

    private TileRendererLayer tileLayer;
    private Polyline routeLine;

    private ActivityType selectedKind = ActivityType.RUNNING;
    private OfflineRoute selectedRoute = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidGraphicFactory.createInstance(getApplication());
        setContentView(R.layout.activity_offline_routes);

        spinnerActivityType = findViewById(R.id.spinnerActivityType);
        mapView = findViewById(R.id.offlineMapView);
        selectedText = findViewById(R.id.textSelectedRoute);
        buttonStart = findViewById(R.id.buttonStartOffline);

        // Map basic setup
        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.getModel().mapViewPosition.setZoomLevel((byte) 12);

        setupActivitySpinner();

        // List offline routes
        RecyclerView rv = findViewById(R.id.routesRecycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        List<OfflineRoute> routes = OfflineRouteStore.listRoutes(this);
        rv.setAdapter(new OfflineRoutesAdapter(routes, this::onRouteSelected));

        buttonStart.setOnClickListener(v -> onStartPressed());
        updateUiForActivityType();
    }

    private void setupActivitySpinner() {
        ActivityType[] kinds = ActivityType.values();

        ArrayAdapter<ActivityType> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                kinds
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerActivityType.setAdapter(adapter);

        spinnerActivityType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedKind = kinds[position];
                updateUiForActivityType();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void updateUiForActivityType() {
        // Keep it identical to StartRecordingActivity’s approach:
        // route only for GPS-related activities
        boolean showRouteUi = selectedKind.isGpsRelated();

        findViewById(R.id.routesRecycler).setVisibility(showRouteUi ? View.VISIBLE : View.GONE);
        mapView.setVisibility(showRouteUi ? View.VISIBLE : View.GONE);
        selectedText.setVisibility(showRouteUi ? View.VISIBLE : View.GONE);

        if (!showRouteUi) {
            selectedRoute = null;
            selectedText.setText("No offline route selected");
            clearRoute();
            clearMap();
        }
    }

    private void onRouteSelected(OfflineRoute r) {
        selectedRoute = r;
        selectedText.setText("Offline route: " + r.name);
        Log.i("OfflineRoutes", "Selected route folder: " + r.name);

        // Preview map + route
        loadOfflineMap(r.mapFile);

        if (r.gpxFile != null && r.gpxFile.exists()) {
            try {
                List<LatLong> pts = parseGpx(r.gpxFile);
                drawRoute(pts);
                if (!pts.isEmpty()) mapView.getModel().mapViewPosition.setCenter(pts.get(0));
            } catch (Exception e) {
                Log.e("OfflineRoutes", "GPX parse failed", e);
                clearRoute();
            }
        } else {
            clearRoute();
        }
    }

    private void onStartPressed() {
        if (!selectedKind.isGpsRelated()) {
            Toast.makeText(this, "Select a GPS-related activity type to use an offline route.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedRoute == null) {
            Toast.makeText(this, "Select an offline route first.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(this, OfflineRoutePreviewActivity.class);
        i.putExtra("activityType", selectedKind.getServerTypeName());
        i.putExtra("offlineRouteName", selectedRoute.name); // folder/name used by OfflineRouteStore
        startActivity(i);
    }

    private void loadOfflineMap(File mapFile) {
        clearMap();

        MapFile mf = new MapFile(mapFile);

        tileLayer = new TileRendererLayer(
                AndroidUtil.createTileCache(
                        this,
                        "offlineCache",
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

    private void clearMap() {
        if (tileLayer != null) {
            mapView.getLayerManager().getLayers().remove(tileLayer);
            tileLayer.onDestroy();
            tileLayer = null;
            mapView.repaint();
        }
    }

    private void drawRoute(List<LatLong> pts) {
        clearRoute();
        if (pts == null || pts.size() < 2) return;

        Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(6);

        routeLine = new Polyline(paint, AndroidGraphicFactory.INSTANCE);
        routeLine.getLatLongs().addAll(pts);

        mapView.getLayerManager().getLayers().add(routeLine);
        mapView.repaint();
    }

    private void clearRoute() {
        if (routeLine != null) {
            mapView.getLayerManager().getLayers().remove(routeLine);
            routeLine = null;
            mapView.repaint();
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tileLayer != null) tileLayer.onDestroy();
        mapView.destroyAll();
    }
}
