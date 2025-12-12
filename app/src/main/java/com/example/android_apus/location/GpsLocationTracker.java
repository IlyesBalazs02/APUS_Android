package com.example.android_apus.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Priority;

public class GpsLocationTracker {

    public interface Listener {
        void onLocationUpdated(double lat, double lon);
    }

    public static final int REQUEST_LOCATION_PERMISSION = 10001;

    private final Activity activity;
    private final Listener listener;
    private final FusedLocationProviderClient fusedClient;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null || listener == null) return;
            android.location.Location last = locationResult.getLastLocation();
            if (last != null) {
                listener.onLocationUpdated(last.getLatitude(), last.getLongitude());
            }
        }
    };

    public GpsLocationTracker(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.fusedClient = LocationServices.getFusedLocationProviderClient(activity);
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION
            );
        }
    }

    @SuppressLint("MissingPermission")
    public void start() {
        if (!hasLocationPermission()) {
            requestLocationPermissionIfNeeded();
            return;
        }

        LocationRequest request = LocationRequest.create();
        request.setInterval(2000);
        request.setFastestInterval(1000);
        request.setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    public void stop() {
        fusedClient.removeLocationUpdates(locationCallback);
    }
}
