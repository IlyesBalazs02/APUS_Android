package com.example.android_apus.tracks;

public class MapsforgeExportRequest {
    public double top;
    public double bottom;
    public double left;
    public double right;
    public String userId;

    public MapsforgeExportRequest(double top, double bottom,
                                  double left, double right,
                                  String userId) {
        this.top = top;
        this.bottom = bottom;
        this.left = left;
        this.right = right;
        this.userId = userId;
    }
}

