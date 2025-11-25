package com.example.android_apus.activity;

public class NonGpsActivityUploadRequest {
    public String activityType;
    public long startTimeUnixSeconds;
    public int durationSeconds;

    public NonGpsActivityUploadRequest(String activityType,
                                       long startTimeUnixSeconds,
                                       int durationSeconds) {
        this.activityType = activityType;
        this.startTimeUnixSeconds = startTimeUnixSeconds;
        this.durationSeconds = durationSeconds;
    }
}