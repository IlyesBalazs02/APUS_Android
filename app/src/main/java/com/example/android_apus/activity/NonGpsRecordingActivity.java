package com.example.android_apus.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.Auth.ApiClient;
import com.example.android_apus.Auth.ApiService;
import com.example.android_apus.Auth.SessionManager;
import com.example.android_apus.R;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NonGpsRecordingActivity extends AppCompatActivity {

    private TextView textActivityName;
    private TextView textTimer;
    private Button buttonPauseResume;
    private Button buttonEnd;

    private boolean isRunning = true;
    private long startTimeMillis;
    private long pausedTimeAccumulated = 0L;
    private long lastPauseStart = 0L;

    private long startTimeUnixSeconds;
    private String activityTypeName;

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

    private SessionManager sessionManager;
    private ApiService apiService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_non_gps_recording);

        textActivityName = findViewById(R.id.textActivityName);
        textTimer = findViewById(R.id.textTimer);
        buttonPauseResume = findViewById(R.id.buttonPauseResume);
        buttonEnd = findViewById(R.id.buttonEnd);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getClient().create(ApiService.class);

        activityTypeName = getIntent().getStringExtra("activityType");
        if (activityTypeName == null) activityTypeName = "Activity";
        textActivityName.setText(activityTypeName);

        startTimeMillis = System.currentTimeMillis();
        startTimeUnixSeconds = startTimeMillis / 1000L;

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
        long elapsedMillis = (now - startTimeMillis) - pausedTimeAccumulated;
        int durationSeconds = (int) (elapsedMillis / 1000L);

        if (durationSeconds <= 0) {
            Toast.makeText(this, "Duration too short", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        NonGpsActivityUploadRequest req = new NonGpsActivityUploadRequest(
                activityTypeName,
                startTimeUnixSeconds,
                durationSeconds
        );

        String token = "Bearer " + sessionManager.getToken();

        apiService.uploadNonGpsActivity(token, req)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(NonGpsRecordingActivity.this,
                                    "Activity uploaded",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(NonGpsRecordingActivity.this,
                                    "Upload failed: " + response.code(),
                                    Toast.LENGTH_LONG).show();
                        }
                        finish();
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(NonGpsRecordingActivity.this,
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);
    }
}