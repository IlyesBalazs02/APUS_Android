package com.example.android_apus.offline;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class OfflineRouteStore {

    private OfflineRouteStore() {}

    public static File rootDir(Context ctx) {
        File root = new File(ctx.getExternalFilesDir(null), "offline_routes");
        if (!root.exists()) root.mkdirs();
        return root;
    }

    public static File routeDir(Context ctx, String routeName) {
        File dir = new File(rootDir(ctx), sanitize(routeName));
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File mapFile(Context ctx, String routeName) {
        return new File(routeDir(ctx, routeName), sanitize(routeName) + ".map");
    }

    public static File gpxFile(Context ctx, String routeName) {
        return new File(routeDir(ctx, routeName), sanitize(routeName) + ".gpx");
    }

    public static List<OfflineRoute> listRoutes(Context ctx) {
        File root = rootDir(ctx);

        File[] mapFiles = root.listFiles((dir, name) ->
                name != null && name.toLowerCase().endsWith(".map"));

        List<OfflineRoute> out = new ArrayList<>();
        if (mapFiles == null) return out;

        for (File map : mapFiles) {
            String baseName = map.getName();
            if (baseName.toLowerCase().endsWith(".map")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }

            File gpx = new File(root, baseName + ".gpx"); // optional
            out.add(new OfflineRoute(baseName, map, gpx.exists() ? gpx : null));
        }

        java.util.Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }


    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9áéíóöőúüűÁÉÍÓÖŐÚÜŰ _-]", "_").trim();
    }
}
