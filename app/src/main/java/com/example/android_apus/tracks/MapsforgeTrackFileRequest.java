package com.example.android_apus.tracks;

public class MapsforgeTrackFileRequest {
    private String trackFileName;

    public MapsforgeTrackFileRequest(String trackFileName) {
        this.trackFileName = trackFileName;
    }

    public String getTrackFileName() {
        return trackFileName;
    }

    public void setTrackFileName(String trackFileName) {
        this.trackFileName = trackFileName;
    }
}