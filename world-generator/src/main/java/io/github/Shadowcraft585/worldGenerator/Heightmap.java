package io.github.Shadowcraft585.worldGenerator;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Heightmap {

    public Map<String, Integer> heights;

    public Heightmap() {
        this.heights = new HashMap<>();
    }

    public Heightmap(Map<String, Integer> backingMap) {
        this.heights = backingMap;
    }

    public void putGridKey(String gridKey, double latGrid, double lonGrid, int height) {
        heights.put(gridKey, height);
    }

    public void put(double lat, double lon, int height) {
        heights.put(roundKey(lat, lon), height);
    }

    public int get(double lat, double lon) {
        double v = getDouble(lat, lon);
        int val = (int) Math.round(v);
        return Math.max(1, Math.min(250, val));
    }

    public double getDouble(double lat, double lon) {

        double latGrid = Math.floor(lat / HeightmapLoader.RES_DEGREES) * HeightmapLoader.RES_DEGREES;
        double lonGrid = Math.floor(lon / HeightmapLoader.RES_DEGREES) * HeightmapLoader.RES_DEGREES;

        double dx = (lon - lonGrid) / HeightmapLoader.RES_DEGREES;
        double dy = (lat - latGrid) / HeightmapLoader.RES_DEGREES;

        String kSW = String.format(Locale.US, "%.6f,%.6f", latGrid, lonGrid);
        String kSE = String.format(Locale.US, "%.6f,%.6f", latGrid, lonGrid + HeightmapLoader.RES_DEGREES);
        String kNW = String.format(Locale.US, "%.6f,%.6f", latGrid + HeightmapLoader.RES_DEGREES, lonGrid);
        String kNE = String.format(Locale.US, "%.6f,%.6f", latGrid + HeightmapLoader.RES_DEGREES, lonGrid + HeightmapLoader.RES_DEGREES);

        Integer vSW = heights.get(kSW);
        Integer vSE = heights.get(kSE);
        Integer vNW = heights.get(kNW);
        Integer vNE = heights.get(kNE);

        java.util.function.BiFunction<Integer, Integer, Double> lerpPair = (a, b) -> {
            if (a != null && b != null) return a * (1.0 - dx) + b * dx;
            if (a != null) return a.doubleValue();
            if (b != null) return b.doubleValue();
            return null;
        };

        Double south = lerpPair.apply(vSW, vSE);
        Double north = lerpPair.apply(vNW, vNE);

        Double interpolated = null;
        if (south != null && north != null) {
            interpolated = south * (1.0 - dy) + north * dy;
        } else if (south != null) {
            interpolated = south;
        } else if (north != null) {
            interpolated = north;
        } else {
            Integer any = vSW != null ? vSW : (vSE != null ? vSE : (vNW != null ? vNW : vNE));
            if (any != null) interpolated = any.doubleValue();
        }

        if (interpolated == null) {
            return 164.0;
        } else {
            return interpolated;
        }
    }

    public boolean hasKey(String gridKey) {
        return heights.containsKey(gridKey);
    }

    public Map<String, Integer> toMap() {
        return new HashMap<>(heights);
    }

    public void loadFromMap(Map<String, Integer> src) {
        heights.putAll(src);
    }

    public String gridKeyFor(double lat, double lon) {
        double latGrid = Math.floor(lat / HeightmapLoader.RES_DEGREES) * HeightmapLoader.RES_DEGREES;
        double lonGrid = Math.floor(lon / HeightmapLoader.RES_DEGREES) * HeightmapLoader.RES_DEGREES;
        return String.format(Locale.US, "%.6f,%.6f", latGrid, lonGrid);
    }

    public String roundKey(double lat, double lon) {
        return String.format(Locale.US, "%.5f,%.5f", lat, lon);
    }
}