package io.github.Shadowcraft585.worldGenerator;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class HeightmapLoader {

    public static HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static double RES_DEGREES = 0.0005;

    public static int BATCH_SIZE = 100;

    public static long PER_BATCH_PAUSE_MS = 500L;

    public static Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    public static int MAX_RETRIES = 4;

    public static String CACHE_FILENAME = "heights_cache.json";

    public static String ELEVATION_URL = "https://api.open-elevation.com/api/v1/lookup?locations=";

    public static Gson GSON = new Gson();

    public static int CONSECUTIVE_429_THRESHOLD = 3;
    public static long LARGE_PAUSE_MS = 60_000L;

    public static Heightmap loadHeights(Iterable<GpsPoint> points) {
        Heightmap map;

        Plugin plugin = Bukkit.getPluginManager().getPlugin("World-Generator-Plugin");
        java.nio.file.Path cachePath;
        if (plugin instanceof JavaPlugin) {
            java.io.File df = ((JavaPlugin) plugin).getDataFolder();
            df.mkdirs();
            cachePath = df.toPath().resolve(CACHE_FILENAME);
        } else {
            cachePath = java.nio.file.Path.of("plugins/World-Generator-Plugin")
                    .resolve(CACHE_FILENAME);
            try {
                Files.createDirectories(cachePath.getParent());
            } catch (IOException ignored) {
            }
        }

        Map<String, Integer> backing = null;
        try {
            File f = cachePath.toFile();
            if (f.exists() && f.isFile()) {
                try (Reader r = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8)) {
                    Type t = new TypeToken<Map<String, Integer>>() {}.getType();
                    backing = GSON.fromJson(r, t);
                    if (backing == null) backing = new HashMap<>();
                    Bukkit.getLogger().info("[HeightmapLoader] Loaded JSON height cache: " + cachePath.toAbsolutePath() + " (entries=" + backing.size() + ")");
                } catch (Exception ex) {
                    Bukkit.getLogger().warning("[HeightmapLoader] Failed to read JSON cache, starting with empty map: " + ex.getMessage());
                    backing = new HashMap<>();
                }
            } else {
                backing = new HashMap<>();
                Bukkit.getLogger().info("[HeightmapLoader] No JSON cache found at " + cachePath.toAbsolutePath());
            }
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[HeightmapLoader] Error while accessing cache path; falling back to in-memory. " + ex.getMessage());
            backing = new HashMap<>();
        }

        map = new Heightmap(backing);

        LinkedHashMap<String, double[]> gridToCoord = new LinkedHashMap<>();
        for (GpsPoint p : points) {
            String gkey = gridKeyFor(p.lat, p.lon);
            if (!gridToCoord.containsKey(gkey)) {
                double[] gc = gridCoordFor(p.lat, p.lon);
                gridToCoord.put(gkey, gc);
            }
        }

        List<String> keys = new ArrayList<>(gridToCoord.keySet());
        int total = keys.size();
        if (total == 0) {
            tryWriteJsonCache(map, cachePath);
            return map;
        }

        int batchIndex = 0;
        int consecutive429 = 0;

        fetchHeights(total, keys, gridToCoord, consecutive429, map, batchIndex);

        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;
        for (double[] c : gridToCoord.values()) {
            if (c[0] < minLat) minLat = c[0];
            if (c[0] > maxLat) maxLat = c[0];
            if (c[1] < minLon) minLon = c[1];
            if (c[1] > maxLon) maxLon = c[1];
        }

        int defaultFallback = 164;
        Set<Integer> placeholders = new HashSet<>(Arrays.asList(64, defaultFallback));

        List<String> fullGridKeys = new ArrayList<>();
        int latSteps = (int) Math.round((maxLat - minLat) / RES_DEGREES);
        int lonSteps = (int) Math.round((maxLon - minLon) / RES_DEGREES);
        latSteps = Math.max(latSteps, 0) + 2;
        lonSteps = Math.max(lonSteps, 0) + 2;

        double startLat = minLat - RES_DEGREES;
        double startLon = minLon - RES_DEGREES;
        for (int iy = 0; iy <= latSteps; iy++) {
            double latGrid = startLat + iy * RES_DEGREES;
            for (int ix = 0; ix <= lonSteps; ix++) {
                double lonGrid = startLon + ix * RES_DEGREES;
                String key = String.format(Locale.US, "%.6f,%.6f", latGrid, lonGrid);
                fullGridKeys.add(key);
                if (!gridToCoord.containsKey(key)) {
                    gridToCoord.put(key, new double[]{latGrid, lonGrid});
                }
            }
        }

        Heightmap finalMap = buildHeightmap(fullGridKeys, map, placeholders, gridToCoord, defaultFallback, cachePath);
        return finalMap;
    }

    private static Heightmap buildHeightmap(List<String> fullGridKeys, Heightmap map, Set<Integer> placeholders, LinkedHashMap<String, double[]> gridToCoord, int defaultFallback, java.nio.file.Path cachePath) {
        int maxPasses = 6;
        int filledByAverage = 0;
        int filledByDefault = 0;

        for (int pass = 0; pass < maxPasses; pass++) {
            Map<String, Integer> newlyFilled = new HashMap<>();
            for (String gkey : fullGridKeys) {
                boolean present = map.hasKey(gkey);
                Integer existing = present ? map.heights.get(gkey) : null;
                if (present && existing != null && !placeholders.contains(existing)) {
                    continue;
                }

                double[] gc = gridToCoord.get(gkey);
                if (gc == null) continue;

                double latGrid = gc[0];
                double lonGrid = gc[1];

                int sum = 0;
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        String nb = String.format(Locale.US, "%.6f,%.6f",
                                latGrid + dy * RES_DEGREES, lonGrid + dx * RES_DEGREES);
                        if (map.hasKey(nb)) {
                            Integer v = map.heights.get(nb);
                            if (v != null && !placeholders.contains(v)) {
                                sum += v;
                                count++;
                            }
                        }
                    }
                }

                if (count > 0) {
                    int avg = (int) Math.round((double) sum / count);
                    newlyFilled.put(gkey, avg);
                }
            }

            if (newlyFilled.isEmpty()) {
                break;
            }

            for (Map.Entry<String, Integer> e : newlyFilled.entrySet()) {
                String k = e.getKey();
                double[] gc = gridToCoord.get(k);
                map.putGridKey(k, gc[0], gc[1], e.getValue());
                filledByAverage++;
            }
        }

        for (String gkey : fullGridKeys) {
            if (!map.hasKey(gkey)) {
                double[] gc = gridToCoord.get(gkey);
                if (gc == null) continue;
                map.putGridKey(gkey, gc[0], gc[1], defaultFallback);
                filledByDefault++;
            } else {
                Integer v = map.heights.get(gkey);
                if (v != null && placeholders.contains(v)) {
                    double[] gc = gridToCoord.get(gkey);
                    map.putGridKey(gkey, gc[0], gc[1], defaultFallback);
                    filledByDefault++;
                }
            }
        }

        Bukkit.getLogger().info(String.format(Locale.US,
                "[HeightmapLoader] Filled grid: neighbor-average=%d, default=%d",
                filledByAverage, filledByDefault));

        tryWriteJsonCache(map, cachePath);

        Bukkit.getLogger().info("[HeightmapLoader] Finished loading heights.");
        return map;
    }

    private static void fetchHeights(int total, List<String> keys, LinkedHashMap<String, double[]> gridToCoord, int consecutive429, Heightmap map, int batchIndex) {
        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(total, i + BATCH_SIZE);
            List<String> batchKeys = keys.subList(i, end);

            String locationsParam = batchKeys.stream()
                    .map(k -> {
                        double[] c = gridToCoord.get(k);
                        return String.format(Locale.US, "%f,%f", c[0], c[1]);
                    })
                    .collect(Collectors.joining("|"));

            String encoded = URLEncoder.encode(locationsParam, StandardCharsets.UTF_8);
            String url = ELEVATION_URL + encoded;

            boolean succeeded = false;
            int attempt = 0;
            long waitMillis = 0L;

            while (attempt <= MAX_RETRIES && !succeeded) {
                if (waitMillis > 0) {
                    try {
                        Thread.sleep(waitMillis);
                    } catch (InterruptedException ignored) {
                    }
                }
                attempt++;

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(REQUEST_TIMEOUT)
                            .header("Accept", "application/json")
                            .header("User-Agent", "WorldGeneratorPlugin/1.0")
                            .GET()
                            .build();

                    HttpResponse<String> response = client.send(request,
                            HttpResponse.BodyHandlers.ofString());
                    int status = response.statusCode();
                    String body = response.body();

                    if (status == 200) {
                        consecutive429 = 0;
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                        com.google.gson.JsonElement resultsElem = json.get("results");
                        if (resultsElem != null && resultsElem.isJsonArray()) {
                            JsonArray arr = resultsElem.getAsJsonArray();
                            for (int idx = 0; idx < arr.size() && idx < batchKeys.size(); idx++) {
                                com.google.gson.JsonObject obj = arr.get(idx).getAsJsonObject();
                                String k = batchKeys.get(idx);
                                double[] c = gridToCoord.get(k);

                                if (obj.has("elevation") && !obj.get("elevation").isJsonNull()) {
                                    int height = (int) Math.round(
                                            obj.get("elevation").getAsDouble());
                                    map.putGridKey(k, c[0], c[1], height);
                                } else {
                                }
                            }
                        } else {
                            Bukkit.getLogger().warning(
                                    "[HeightmapLoader] Unexpected JSON " + (batchIndex + 1)
                                            + " body:" + body);
                        }
                        succeeded = true;
                    } else if (status == 429) {
                        consecutive429++;
                        Optional<String> ra = response.headers().firstValue("Retry-After");
                        Long raSec = null;
                        if (ra.isPresent()) {
                            try {
                                raSec = Long.parseLong(ra.get().trim());
                            } catch (Exception ex) {
                                raSec = null;
                            }
                        }
                        if (raSec != null && raSec > 0) {
                            waitMillis = raSec * 1000L;
                            Bukkit.getLogger().warning(
                                    "[HeightmapLoader] 429 received; will retry after Retry-After="
                                            + raSec + "s (batch " + (batchIndex + 1) + ").");
                        } else {
                            waitMillis = 2000L * attempt + (long) (Math.random() * 1000.0);
                            Bukkit.getLogger().warning(
                                    "[HeightmapLoader] 429 received; retrying with backoff "
                                            + waitMillis + "ms (attempt " + attempt + ").");
                        }
                        if (consecutive429 > CONSECUTIVE_429_THRESHOLD) {
                            Bukkit.getLogger().warning(
                                    "[HeightmapLoader] Many 429s in a row; pausing "
                                            + LARGE_PAUSE_MS + "ms.");
                            try {
                                Thread.sleep(LARGE_PAUSE_MS);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    } else if (status >= 500) {
                        Bukkit.getLogger().warning("[HeightmapLoader] Server error " + status
                                + " ; backing off " + waitMillis + "ms.");
                    } else {
                        Bukkit.getLogger().warning("[HeightmapLoader] Non-200 response (" + status
                                + ") for batch " + (batchIndex + 1) + " : " + body);
                        succeeded = true;
                    }
                } catch (Exception ex) {
                    Bukkit.getLogger().warning(ex.getMessage());
                }
            }

            try {
                Thread.sleep(PER_BATCH_PAUSE_MS);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public static String gridKeyFor(double lat, double lon) {
        double[] g = gridCoordFor(lat, lon);
        return String.format(Locale.US, "%.6f,%.6f", g[0], g[1]);
    }

    public static double[] gridCoordFor(double lat, double lon) {
        double latGrid = Math.floor(lat / RES_DEGREES) * RES_DEGREES;
        double lonGrid = Math.floor(lon / RES_DEGREES) * RES_DEGREES;
        return new double[]{latGrid, lonGrid};
    }

    private static void tryWriteJsonCache(Heightmap map, java.nio.file.Path cachePath) {
        try {
            Map<String, Integer> dump = map.toMap();
            java.nio.file.Path tmp = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(dump, w);
            }
            Files.move(tmp, cachePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            Bukkit.getLogger().info("[HeightmapLoader] Cached " + dump.size() + " heights to " + cachePath.toAbsolutePath());
        } catch (Exception e) {
            Bukkit.getLogger().warning("[HeightmapLoader] Failed to write JSON cache: " + e.getMessage());
        }
    }
}