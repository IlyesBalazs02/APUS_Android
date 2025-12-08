package com.example.android_apus;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.android_apus.Auth.ApiClient;
import com.example.android_apus.Auth.ApiService;
import com.example.android_apus.Auth.SessionManager;
import com.example.android_apus.tracks.CoordinateDto;
import com.example.android_apus.tracks.TracksActivity;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;
import com.mapbox.maps.extension.style.layers.generated.LineLayer;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;          // IMPORTANT: retrofit Call, not android.telecom.Call
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

}
