package io.github.Shadowcraft585.worldGenerator;

public class GpsPoint {
    public double lat;
    public double lon;
    public String type;

    public GpsPoint(double lat, double lon, String type) {
        this.lat = lat;
        this.lon = lon;
        this.type = type;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public String getType() {
        return type;
    }
}