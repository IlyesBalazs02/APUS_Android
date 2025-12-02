package com.example.android_apus.offline;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android_apus.R;

import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.map.android.util.AndroidUtil;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class OfflineRoutesActivity extends AppCompatActivity {

    private MapView mapView;
    private TextView selectedText;
    private TileRendererLayer tileLayer;
    private Polyline routeLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidGraphicFactory.createInstance(getApplication());

        setContentView(R.layout.activity_offline_routes);

        mapView = findViewById(R.id.offlineMapView);
        selectedText = findViewById(R.id.textSelectedRoute);

        // Basic map setup
        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.getModel().mapViewPosition.setZoomLevel((byte) 12);

        // Load saved routes list
        RecyclerView rv = findViewById(R.id.routesRecycler);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<OfflineRoute> routes = OfflineRouteStore.listRoutes(this);
        rv.setAdapter(new OfflineRoutesAdapter(routes, this::onRouteSelected));
    }

    private void onRouteSelected(OfflineRoute r) {
        selectedText.setText("Selected: " + r.name);
        Log.i("OfflineRoutes", "Loading map: " + r.mapFile.getAbsolutePath());

        loadOfflineMap(r.mapFile);

        if (r.gpxFile != null && r.gpxFile.exists()) {
            try {
                List<LatLong> pts = parseGpx(r.gpxFile);
                drawRoute(pts);
                if (!pts.isEmpty()) mapView.getModel().mapViewPosition.setCenter(pts.get(0));
            } catch (Exception e) {
                Log.e("OfflineRoutes", "GPX parse failed", e);
            }
        } else {
            clearRoute();
        }
    }

    private void loadOfflineMap(File mapFile) {
        // Remove previous layer
        if (tileLayer != null) {
            mapView.getLayerManager().getLayers().remove(tileLayer);
            tileLayer.onDestroy();
            tileLayer = null;
        }

        MapFile mf = new MapFile(mapFile);

        tileLayer = new TileRendererLayer(
                AndroidUtil.createTileCache(this, "offlineCache", mapView.getModel().displayModel.getTileSize(), 1f,
                        mapView.getModel().frameBufferModel.getOverdrawFactor()),
                mf,
                mapView.getModel().mapViewPosition,
                AndroidGraphicFactory.INSTANCE
        );

        tileLayer.setXmlRenderTheme(InternalRenderTheme.DEFAULT);
        mapView.getLayerManager().getLayers().add(tileLayer);
        mapView.repaint();
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
        // Minimal trkpt parser (lat/lon). Replace with your GpsRecordingActivity parsing if needed.
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
