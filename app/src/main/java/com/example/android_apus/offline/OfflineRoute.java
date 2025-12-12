package com.example.android_apus.offline;

import java.io.File;

public class OfflineRoute {
    public final String name;
    public final File mapFile;
    public final File gpxFile;

    public OfflineRoute(String name, File mapFile, File gpxFile) {
        this.name = name;
        this.mapFile = mapFile;
        this.gpxFile = gpxFile;
    }
}
