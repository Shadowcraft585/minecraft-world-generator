package io.github.Shadowcraft585.worldGenerator;

import java.util.List;

public class WayData {
    public List<GpsPoint> pts;
    public int halfWidth;

    public WayData(List<GpsPoint> pts, int halfWidth) {
        this.pts = pts;
        this.halfWidth = halfWidth;
    }
}