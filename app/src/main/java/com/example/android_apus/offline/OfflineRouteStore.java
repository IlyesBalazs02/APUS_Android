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
        File[] dirs = root.listFiles(File::isDirectory);
        List<OfflineRoute> out = new ArrayList<>();
        if (dirs == null) return out;

        for (File d : dirs) {
            String name = d.getName();
            File map = new File(d, name + ".map");
            File gpx = new File(d, name + ".gpx");
            if (map.exists()) {
                out.add(new OfflineRoute(name, map, gpx.exists() ? gpx : null));
            }
        }
        return out;
    }

    private static String sanitize(String s) {
        // Keep it file-system safe
        return s.replaceAll("[^a-zA-Z0-9áéíóöőúüűÁÉÍÓÖŐÚÜŰ _-]", "_").trim();
    }
}
