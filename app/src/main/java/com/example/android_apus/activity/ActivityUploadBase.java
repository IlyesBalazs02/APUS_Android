package com.example.android_apus.activity;

import java.time.Instant;

// Base for both GPS and non-GPS
public class ActivityUploadBase {
    public String title;
    public String description;
    public String activityType;
    public Instant startTimeUtc;
    public long durationSeconds;
    public Integer calories;
    public Integer avgHeartRate;
    public Integer maxHeartRate;
}