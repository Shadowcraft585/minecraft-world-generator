package io.github.Shadowcraft585.worldGenerator;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

public class Raster {

    public Raster() {
    }

    public static int latToBlockZ(double lat) {
        double v = Math.floor(lat * 111320.0);
        return (int) Math.round(-v);
    }

    public static int lonToBlockX(double lon, double lat) {
        double v = lon * 111320.0 * Math.cos(Math.toRadians(lat));
        return (int) Math.round(v);
    }

    public static long keyFor(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }

    public static void rasterizeSegment(Set<Long> out, int x0, int z0, int x1, int z1, int halfWidth) {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        int x = x0, z = z0;
        while (true) {
            for (int ox = -halfWidth; ox <= halfWidth; ox++) {
                for (int oz = -halfWidth; oz <= halfWidth; oz++) {
                    out.add(keyFor(x + ox, z + oz));
                }
            }

            if (x == x1 && z == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
    }

    public static void rasterizeWay(
            Set<Long> out,
            List<GpsPoint> pts,
            int halfWidth
    ) {
        if (pts == null || pts.size() < 2) return;

        int prevX = lonToBlockX(pts.get(0).lon, pts.get(0).lat);
        int prevZ = latToBlockZ(pts.get(0).lat);

        for (int i = 1; i < pts.size(); i++) {
            GpsPoint p = pts.get(i);
            int x = lonToBlockX(p.lon, p.lat);
            int z = latToBlockZ(p.lat);
            rasterizeSegment(out, prevX, prevZ, x, z, halfWidth);
            prevX = x;
            prevZ = z;
        }
    }

    public static Set<Long> rasterizeWays(
            List<WayData> ways
    ) {
        Set<Long> out = new HashSet<>();
        if (ways == null) return out;
        for (WayData way : ways) {
            rasterizeWay(out, way.pts, way.halfWidth);
        }
        return out;
    }

    public static Set<Long> dilate(Set<Long> in, int radius) {
        if (radius <= 0) return new HashSet<>(in);
        Set<Long> out = new HashSet<>(in);
        for (long k : in) {
            int x = (int) (k >> 32);
            int z = (int) k;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    out.add(keyFor(x + dx, z + dz));
                }
            }
        }
        return out;
    }

    public static void closeSmallGaps(Set<Long> blocks, int maxGap) {
        if (maxGap <= 0 || blocks.size() < 2) return;

        List<Long> list = new ArrayList<>(blocks);

        for (long k : list) {
            int x1 = (int) (k >> 32);
            int z1 = (int) k;

            for (int dx = -maxGap; dx <= maxGap; dx++) {
                for (int dz = -maxGap; dz <= maxGap; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    int x2 = x1 + dx;
                    int z2 = z1 + dz;

                    if (x2 < x1) continue;
                    if (x2 == x1 && z2 <= z1) continue;

                    long key2 = keyFor(x2, z2);
                    if (blocks.contains(key2)) {
                        rasterizeSegment(blocks, x1, z1, x2, z2, 0);
                    }
                }
            }
        }
    }
}