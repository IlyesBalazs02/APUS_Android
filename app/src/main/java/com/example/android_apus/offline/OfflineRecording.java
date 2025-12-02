package com.example.android_apus.offline;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.R;

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

public class OfflineRecording extends AppCompatActivity {

    public static final String EXTRA_ROUTE_NAME = "routeName";

    private MapView mapView;
    private TextView title;

    private TileRendererLayer tileLayer;
    private Polyline routeLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidGraphicFactory.createInstance(getApplication());
        setContentView(R.layout.activity_offline_recording);

        mapView = findViewById(R.id.mapView);

        String routeName = getIntent().getStringExtra(EXTRA_ROUTE_NAME);
        if (routeName == null || routeName.trim().isEmpty()) {
            Toast.makeText(this, "No route selected.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }


        // mapView basics
        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.getModel().mapViewPosition.setZoomLevel((byte) 12);

        File mapFile = new File(OfflineRouteStore.rootDir(this), routeName + ".map");
        File gpxFile = new File(OfflineRouteStore.rootDir(this), routeName + ".gpx");

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
            Log.e("OfflinePreview", "Failed to load offline preview", e);
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadOfflineMap(File mapFile) {
        MapFile mf = new MapFile(mapFile);

        tileLayer = new TileRendererLayer(
                AndroidUtil.createTileCache(
                        this,
                        "offlinePreviewCache",
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tileLayer != null) tileLayer.onDestroy();
        mapView.destroyAll();
    }
}
