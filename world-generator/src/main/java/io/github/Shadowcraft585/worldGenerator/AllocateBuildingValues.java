package io.github.Shadowcraft585.worldGenerator;

import java.util.Random;

public class AllocateBuildingValues {

    public static final Random random = new Random();

    private static double parseDoubleOr(String keyParent, String keyName, double fallback) {
        try {
            String s = XMLReader.getVariable(keyParent, keyName);
            if (s == null) return fallback;
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static double polygonAreaBlocks(int[] xs, int[] zs) {
        if (xs == null || zs == null || xs.length < 3 || zs.length < 3) return 0.0;
        int n = Math.min(xs.length, zs.length);
        long sum = 0L;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            sum += (long) xs[i] * (long) zs[j] - (long) xs[j] * (long) zs[i];
        }
        return Math.abs(sum) / 2.0;
    }

    public static int estimateFloors(BuildingPolygon bp) {
        if (bp == null) return 1;
        if (bp.explicitLevels > 0) return Math.max(1, bp.explicitLevels);

        double heightMeters = bp.heightMeters;
        double floorMeters = HighwayLoader.FloorHieght > 0 ? HighwayLoader.FloorHieght : 3.0;
        if (heightMeters > 0.0) {
            int est = Math.max(1, (int) Math.round(heightMeters / floorMeters));
            return est;
        }

        return 1;
    }

    public static double setPriceValue(BuildingPolygon bp) {
        if (bp == null) return 0.0;

        double area = polygonAreaBlocks(bp.xs, bp.zs);
        int floors = estimateFloors(bp);

        double basePrice = 20.0;
        double variancePercent = 0.10;

        String basePriceString = XMLReader.getVariable("Monopoly", "pricePerBlockPerFloor");
        try {
            basePrice = Double.parseDouble(basePriceString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String variancePercentString = XMLReader.getVariable("Monopoly", "plusMinusValueRandomnessPercent");
        try {
            variancePercent = Double.parseDouble(variancePercentString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        double raw = area * (double) floors * basePrice;

        double factor = 1.0 + variancePercent;
        return raw * factor;
    }

    public static double setEarningPerSecond(BuildingPolygon bp) {
        if (bp == null) return 0.0;

        double area = polygonAreaBlocks(bp.xs, bp.zs);
        int floors = estimateFloors(bp);

        double baseEarning = 0.01;
        double variancePercent = 0.10;

        String baseEarningString = XMLReader.getVariable("Monopoly", "earningPerBlockPerFloorPerSecond");
        try {
            baseEarning = Double.parseDouble(baseEarningString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String variancePercentString = XMLReader.getVariable("Monopoly", "plusMinusEarningRandomnessPercent");
        try {
            variancePercent = Double.parseDouble(variancePercentString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        double raw = area * (double) floors * baseEarning;

        double factor = 1.0 + variancePercent;
        return raw * factor;
    }
}