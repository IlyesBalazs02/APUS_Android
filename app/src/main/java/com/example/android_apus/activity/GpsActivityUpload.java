package com.example.android_apus.activity;
import java.time.Instant;

public class GpsActivityUpload extends ActivityUploadBase {
    public Double totalDistanceKm;
    public Double totalAscentMeters;
    public Double totalDescentMeters;
    public Double avgPace;           // m/s
    public Instant finishTimeUtc;
    // later we can also send GPX/tcx or raw trackpoints
}
