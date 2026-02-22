package io.github.Shadowcraft585.worldGenerator;

import java.util.List;
import java.awt.Polygon;
import org.bukkit.Material;

public class BuildingPolygon {
    public List<GpsPoint> coords;
    public int[] xs;
    public int[] zs;
    public double heightMeters;

    public Polygon poly;

    public int baseBlockY = -1;
    public int minBlockY = -1;

    public int doorX = Integer.MIN_VALUE;
    public int doorZ = Integer.MIN_VALUE;
    public int doorDir = -1;
    public int explicitLevels = -1;

    public int ladderX = Integer.MIN_VALUE;
    public int ladderZ = Integer.MIN_VALUE;
    public int ladderDir = -1;

    public int chestX = Integer.MIN_VALUE;
    public int chestZ = Integer.MIN_VALUE;
    public int chestY = Integer.MIN_VALUE;

    public Material buildingMaterial;

    public double earning = 0;
    public double value = 0;

    public String roofShape;
    public String streetName;

    public BuildingPolygon(List<GpsPoint> coords, int[] xs, int[] zs, double heightMeters, Material buildingMaterial,
                           String roofShape, String streetName) {
        this.coords = coords;

        this.value = value;
        this.earning = earning;

        this.xs = xs;
        this.zs = zs;
        this.heightMeters = heightMeters;

        this.buildingMaterial = buildingMaterial;

        this.roofShape = roofShape;
        this.streetName = streetName;

        if (this.xs != null && this.zs != null && this.xs.length >= 3 && this.zs.length >= 3) {
            int n = Math.min(this.xs.length, this.zs.length);
            this.poly = new Polygon(this.xs, this.zs, n);
        } else {
            this.poly = null;
        }
    }

    public boolean contains(int px, int pz) {
        if (poly == null) return false;
        return poly.contains(px, pz);
    }

    public double getEarning() {
        return earning;
    }
    public void setEarning(double earning) {
        this.earning = earning;
    }

    public double getValue() {
        return value;
    }
    public void setValue(double value) {
        this.value = value;
    }

    public String getStreetName() {
        return streetName;
    }
}