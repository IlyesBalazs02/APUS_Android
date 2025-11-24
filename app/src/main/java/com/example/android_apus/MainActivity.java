package com.example.android_apus;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.tracks.TracksActivity;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Root layout
        FrameLayout root = new FrameLayout(this);

        // Your working Mapbox MapView
        mapView = new MapView(this);
        FrameLayout.LayoutParams mapLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(mapView, mapLp);

        // Load style and center on Margit Island
        mapView.getMapboxMap().loadStyleUri(Style.STANDARD, style -> {
            mapView.getMapboxMap().setCamera(
                    new CameraOptions.Builder()
                            .center(Point.fromLngLat(19.0474, 47.5316)) // Margit Island
                            .pitch(0.0)
                            .zoom(15.0)
                            .bearing(0.0)
                            .build()
            );
        });

        // "My tracks" button overlay
        Button btnShowTracks = new Button(this);
        btnShowTracks.setText("My tracks");
        btnShowTracks.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
        btnShowTracks.setTextColor(getResources().getColor(android.R.color.white));

        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        btnLp.setMargins(margin, margin, margin, margin);

        root.addView(btnShowTracks, btnLp);

        // Button click → open TracksActivity
        btnShowTracks.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TracksActivity.class);
            startActivity(intent);
        });

        setContentView(root);
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
