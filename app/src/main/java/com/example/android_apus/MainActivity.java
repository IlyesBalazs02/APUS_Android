package com.example.android_apus;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mapView = new MapView(this);

        // Optionally, explicitly load a style (recommended)
        mapView.getMapboxMap().loadStyleUri(Style.STANDARD);

        // Center on Margit-sziget, Budapest
        mapView.getMapboxMap().setCamera(
                new CameraOptions.Builder()
                        .center(Point.fromLngLat(19.0474, 47.5316)) // Margit Island
                        .pitch(0.0)
                        .zoom(15.0)   // closer in
                        .bearing(0.0)
                        .build()
        );

        setContentView(mapView);
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
