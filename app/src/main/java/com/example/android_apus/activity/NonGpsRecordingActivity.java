package com.example.android_apus.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.R;

public class NonGpsRecordingActivity extends AppCompatActivity {

    private TextView textActivityName;
    private TextView textTimer;
    private Button buttonPauseResume;
    private Button buttonEnd;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_non_gps_recording);

        textActivityName = findViewById(R.id.textActivityName);
        textTimer = findViewById(R.id.textTimer);
        buttonPauseResume = findViewById(R.id.buttonPauseResume);
        buttonEnd = findViewById(R.id.buttonEnd);

        activityTypeName = getIntent().getStringExtra("activityType");
        if (activityTypeName == null) activityTypeName = "Activity";

        textActivityName.setText(activityTypeName);

        startTimeMillis = System.currentTimeMillis();
        handler.post(timerRunnable);

        buttonPauseResume.setOnClickListener(v -> onPauseResume());
        buttonEnd.setOnClickListener(v -> onEnd());
    }

    private void onPauseResume() {
        if (isRunning) {
            // pause
            isRunning = false;
            buttonPauseResume.setText("Resume");
            lastPauseStart = System.currentTimeMillis();
        } else {
            // resume
            isRunning = true;
            buttonPauseResume.setText("Pause");
            long now = System.currentTimeMillis();
            pausedTimeAccumulated += (now - lastPauseStart);
        }
    }

    private void onEnd() {
        long now = System.currentTimeMillis();
        long elapsed = (now - startTimeMillis) - pausedTimeAccumulated;

        // TODO: create NonGpsActivityUpload and send to server later
        Toast.makeText(this,
                "Activity finished, duration: " + formatDuration(elapsed),
                Toast.LENGTH_LONG).show();

        // finish for now
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
    }
}