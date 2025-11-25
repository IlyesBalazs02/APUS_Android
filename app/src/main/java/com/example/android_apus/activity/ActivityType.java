package com.example.android_apus.activity;

public enum ActivityType {
    RUNNING("Running", true),
    HIKING("Hiking", true),
    WALK("Walk", true),
    RIDE("Ride", true),
    SKI("Ski", true),

    YOGA("Yoga", false),
    BOULDERING("Bouldering", false),
    ROCK_CLIMBING("RockClimbing", false),
    FOOTBALL("Football", false),
    SWIMMING("Swimming", false),
    TENNIS("Tennis", false);

    private final String serverTypeName;
    private final boolean gpsRelated;

    ActivityType(String serverTypeName, boolean gpsRelated) {
        this.serverTypeName = serverTypeName;
        this.gpsRelated = gpsRelated;
    }

    public String getServerTypeName() {
        return serverTypeName;
    }

    public boolean isGpsRelated() {
        return gpsRelated;
    }

    @Override
    public String toString() {
        return serverTypeName;
    }
}
