package com.example.android_apus.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.R;

public class GpsRecordingActivity extends AppCompatActivity {

    private TextView textActivityName;
    private TextView textTimer;
    private Button buttonPauseResume;
    private Button buttonEnd;
    private FrameLayout mapContainer;

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
        mapContainer = findViewById(R.id.mapContainer);

        activityTypeName = getIntent().getStringExtra("activityType");
        if (activityTypeName == null) activityTypeName = "Activity";

        routeFileName = getIntent().getStringExtra("routeFileName");

        textActivityName.setText(activityTypeName);

        // TODO: initialize map view (MapLibre / Mapsforge) in mapContainer
        // TODO: if routeFileName != null, load that track and display polyline

        startTimeMillis = System.currentTimeMillis();
        handler.post(timerRunnable);

        buttonPauseResume.setOnClickListener(v -> onPauseResume());
        buttonEnd.setOnClickListener(v -> onEnd());
    }

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

        // TODO: stop GPS tracking, build GpsActivityUpload DTO, upload to server
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);
        // TODO: cleanup map + GPS
    }
}
