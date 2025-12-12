package com.example.android_apus.activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.R;
import com.example.android_apus.tracks.TracksActivity;

public class StartRecordingActivity extends AppCompatActivity {

    private static final int REQ_SELECT_ROUTE = 1001;

    private Spinner spinnerActivityType;
    private Button buttonSelectRoute;
    private TextView textSelectedRoute;
    private Button buttonStartRecording;

    private ActivityType selectedKind = ActivityType.RUNNING;
    private String selectedRouteFileName = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_recording);

        spinnerActivityType = findViewById(R.id.spinnerActivityType);
        buttonSelectRoute = findViewById(R.id.buttonSelectRoute);
        textSelectedRoute = findViewById(R.id.textSelectedRoute);
        buttonStartRecording = findViewById(R.id.buttonStartRecording);
        Button buttonOpenTracks = findViewById(R.id.buttonOpenTracks);

        buttonOpenTracks.setOnClickListener(v -> {
            Intent i = new Intent(this, TracksActivity.class);
            startActivity(i);
        });

        setupActivitySpinner();
        setupButtons();
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

        spinnerActivityType.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(android.widget.AdapterView<?> parent,
                                               View view,
                                               int position,
                                               long id) {
                        selectedKind = kinds[position];
                        updateRouteButtonVisibility();
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    }
                });
    }

    private void updateRouteButtonVisibility() {
        if (selectedKind.isGpsRelated()) {
            buttonSelectRoute.setVisibility(View.VISIBLE);
            textSelectedRoute.setVisibility(View.VISIBLE);
        } else {
            buttonSelectRoute.setVisibility(View.GONE);
            textSelectedRoute.setVisibility(View.GONE);
            selectedRouteFileName = null;
        }
    }

    private void setupButtons() {
        buttonSelectRoute.setOnClickListener(v -> {
            Intent i = new Intent(this, TracksActivity.class);
            startActivityForResult(i, REQ_SELECT_ROUTE);
        });

        buttonStartRecording.setOnClickListener(v -> {
            if (selectedKind.isGpsRelated()) {
                startGpsRecording();
            } else {
                startNonGpsRecording();
            }
        });
    }

    private void startGpsRecording() {
        Intent i = new Intent(this, GpsRecordingActivity.class);
        i.putExtra("activityType", selectedKind.getServerTypeName());
        if (selectedRouteFileName != null) {
            i.putExtra("routeFileName", selectedRouteFileName);
        }
        startActivity(i);
    }



    private void startNonGpsRecording() {
        Intent i = new Intent(this, NonGpsRecordingActivity.class);
        i.putExtra("activityType", selectedKind.getServerTypeName());
        startActivity(i);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_SELECT_ROUTE && resultCode == RESULT_OK && data != null) {
            selectedRouteFileName = data.getStringExtra("selectedTrack");
            if (selectedRouteFileName != null) {
                textSelectedRoute.setText("Route: " + selectedRouteFileName);
            } else {
                textSelectedRoute.setText("No route selected");
            }
        }
    }
}