package io.github.Shadowcraft585.worldGenerator;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.Locale;

public class HighwayLoader {

    public static HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public static double BLOCKS_PER_LANE = 2.0;
    public static int DILATION_RADIUS = 1;
    public static int GAP_CLOSE_MAX = 3;

    public static double METERS_PER_BLOCK = 1.0;

    public static int buildingHeight = 8;

    public static double FloorHieght = 3.0;

    public static MapData load(double south, double west, double north, double east) {

        String defaultFloorHieght = XMLReader.getVariable("HighwayLoader", "defaultFloorHieght");
        FloorHieght = Double.parseDouble(defaultFloorHieght);

        String defaultBuildingHeight = XMLReader.getVariable("HighwayLoader", "defaultBuildingHeight");
        buildingHeight = Integer.parseInt(defaultBuildingHeight);

        List<WayData> highwayWays = new ArrayList<>();
        List<BuildingPolygon> buildings = new ArrayList<>();
        List<WayData> railwayWays = new ArrayList<>();

        int explicitHeightCount = 0;
        int estimatedHeightCount = 0;

        try {
            String bbox = String.format(Locale.US, "%f,%f,%f,%f", south, west, north, east);

            String query = String.format(Locale.US, """
                            [out:json][timeout:90];
                            (
                              way["building"](%s);
                              way["highway"](%s);
                              way["landuse"](%s);
                              way["natural"](%s);
                              way["amenity"](%s);
                              way["leisure"](%s);
                              way["railway"](%s);
                              relation["building"](%s);
                              relation["landuse"](%s);
                              relation["natural"](%s);
                              relation["amenity"](%s);
                              relation["leisure"](%s);
                              relation["railway"](%s);
                            );
                            out body;
                            >;
                            out geom;
                            """,
                    bbox, bbox, bbox, bbox, bbox, bbox, bbox, bbox, bbox, bbox, bbox, bbox, bbox
            );

            String body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

            Path cachePath;
            try {
                org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("World-Generator-Plugin");
                if (plugin != null && plugin instanceof org.bukkit.plugin.java.JavaPlugin) {
                    File df = ((org.bukkit.plugin.java.JavaPlugin) plugin).getDataFolder();
                    df.mkdirs();
                    cachePath = df.toPath().resolve("overpass_cache.json");
                } else {
                    Path base = Path.of("plugins/World-Generator-Plugin");
                    Files.createDirectories(base);
                    cachePath = base.resolve("overpass_cache.json");
                }
            } catch (Exception e) {
                cachePath = Path.of("overpass_cache.json");
            }

            String respBody = null;
            int status = -1;

            try {
                if (Files.exists(cachePath) && Files.isRegularFile(cachePath)) {
                    respBody = Files.readString(cachePath, StandardCharsets.UTF_8);
                    status = 200;
                    Bukkit.getLogger().info("[HighwayLoader] Using cached Overpass response: " + cachePath.toAbsolutePath());
                }
            } catch (Exception ignored) {
            }

            if (respBody == null) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://overpass-api.de/api/interpreter"))
                        .timeout(Duration.ofSeconds(90))
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .header("Accept", "application/json")
                        .header("User-Agent", "WorldGeneratorPlugin/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                status = response.statusCode();
                respBody = response.body();

                if (status == 200 && respBody != null && !respBody.isBlank()) {
                    try {
                        Path parent = cachePath.getParent();
                        if (parent != null) Files.createDirectories(parent);
                        Path tmp = cachePath.resolveSibling(cachePath.getFileName().toString() + ".tmp");
                        try (java.io.Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                            w.write(respBody);
                        }
                        Files.move(tmp, cachePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                        Bukkit.getLogger().info("[HighwayLoader] Wrote Overpass cache to " + cachePath.toAbsolutePath());
                    } catch (Exception ex) {
                        Bukkit.getLogger().warning("[HighwayLoader] Failed to write Overpass cache: " + ex.getMessage());
                    }
                }
            }

            if (status != 200) {
                String preview = respBody == null ? "<empty>" : respBody.substring(0, Math.min(respBody.length(), 2000));
                Bukkit.getLogger().warning("Overpass non-200 response: " + status + " preview:\n" + preview);
                return new MapData(Collections.emptyList(), Collections.emptyList(), new Heightmap(),
                        Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                        Collections.emptyMap(), Collections.emptyMap());
            }
            if (respBody == null || respBody.isBlank()) {
                Bukkit.getLogger().warning("Empty Overpass response.");
                return new MapData(Collections.emptyList(), Collections.emptyList(), new Heightmap(),
                        Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                        Collections.emptyMap(), Collections.emptyMap());
            }

            JsonObject root = JsonParser.parseString(respBody).getAsJsonObject();
            JsonArray elements = root.getAsJsonArray("elements");
            if (elements == null) {
                Bukkit.getLogger().warning("No elements in Overpass response.");
                return new MapData(Collections.emptyList(), Collections.emptyList(), new Heightmap(),
                        Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                        Collections.emptyMap(), Collections.emptyMap());
            }

            Map<String, JsonObject> elementByKey = new HashMap<>();
            Map<String, JsonObject> tagsByKey = new HashMap<>();
            Map<String, JsonArray> geomByKey = new HashMap<>();

            Map<Long, Double> nodeLat = new HashMap<>();
            Map<Long, Double> nodeLon = new HashMap<>();

            for (JsonElement el : elements) {
                JsonObject obj = el.getAsJsonObject();
                String type = obj.has("type") ? obj.get("type").getAsString() : null;
                if (type == null) continue;
                long id = obj.has("id") ? obj.get("id").getAsLong() : -1L;
                if (id == -1L) continue;
                String key = type + ":" + id;
                elementByKey.put(key, obj);

                if (obj.has("tags")) tagsByKey.put(key, obj.getAsJsonObject("tags"));
                if (obj.has("geometry")) geomByKey.put(key, obj.getAsJsonArray("geometry"));
                if ("node".equals(type) && obj.has("lat") && obj.has("lon")) {
                    nodeLat.put(id, obj.get("lat").getAsDouble());
                    nodeLon.put(id, obj.get("lon").getAsDouble());
                }
            }

            extractJson(tagsByKey, geomByKey, elementByKey, nodeLat, nodeLon, highwayWays, explicitHeightCount,
                    estimatedHeightCount, buildings, railwayWays);

            Set<Long> roadBlocksSet = Raster.rasterizeWays(highwayWays);
            Bukkit.getLogger().info("[HighwayLoader] Raw rasterized road blocks: " + roadBlocksSet.size());
            roadBlocksSet = Raster.dilate(roadBlocksSet, DILATION_RADIUS);
            Raster.closeSmallGaps(roadBlocksSet, GAP_CLOSE_MAX);
            Bukkit.getLogger().info("[HighwayLoader] Post-processed road blocks: " + roadBlocksSet.size());

            Map<Long, String> roadMap = new HashMap<>();
            for (Long k : roadBlocksSet) roadMap.put(k, "road");

            Set<Long> railBlocksSet = Raster.rasterizeWays(railwayWays);
            Bukkit.getLogger().info("[HighwayLoader] Raw rasterized rail blocks: " + railBlocksSet.size());
            railBlocksSet = Raster.dilate(railBlocksSet, 0);
            Map<Long, String> railMap = new HashMap<>();
            for (Long k : railBlocksSet) railMap.put(k, "rail");

            Map<Long, String> landuseMap = new HashMap<>();
            Map<Long, String> naturalMap = new HashMap<>();
            Map<Long, String> amenityMap = new HashMap<>();

            extractTags(tagsByKey, geomByKey, elementByKey, nodeLat, nodeLon, naturalMap, amenityMap, landuseMap);

            Bukkit.getLogger().info("[HighwayLoader] landuse blocks: " + landuseMap.size()
                    + ", natural blocks: " + naturalMap.size() + ", amenity blocks: " + amenityMap.size());

            List<GpsPoint> allPointsForHeights = new ArrayList<>();
            Set<String> added = new HashSet<>();
            for (WayData way : highwayWays) {
                for (GpsPoint gp : way.pts) {
                    String k = String.format(Locale.US, "%.5f,%.5f", gp.lat, gp.lon);
                    if (added.add(k)) allPointsForHeights.add(gp);
                }
            }
            for (BuildingPolygon bp : buildings) {
                for (GpsPoint gp : bp.coords) {
                    String k = String.format(Locale.US, "%.5f,%.5f", gp.lat, gp.lon);
                    if (added.add(k)) allPointsForHeights.add(gp);
                }
            }

            Bukkit.getLogger().info("HighwayLoader Requesting heights for " + allPointsForHeights.size() + " unique coordinates.");
            Heightmap heightmap = HeightmapLoader.loadHeights(allPointsForHeights);

            double minMeters = Double.POSITIVE_INFINITY;
            double maxMeters = Double.NEGATIVE_INFINITY;
            for (GpsPoint gp : allPointsForHeights) {
                double h = heightmap.getDouble(gp.lat, gp.lon);
                if (Double.isFinite(h)) {
                    if (h < minMeters) minMeters = h;
                    if (h > maxMeters) maxMeters = h;
                }
            }
            if (!Double.isFinite(minMeters) || !Double.isFinite(maxMeters)) {
                minMeters = 0.0;
                maxMeters = 164.0;
            }

            double spanMeters = maxMeters - minMeters;

            double computedScale = Double.POSITIVE_INFINITY;
            if (spanMeters > 0.0) {
                computedScale = (double) (CustomChunkGenerator.MC_TARGET_MAX_Y - CustomChunkGenerator.MC_MIN_Y) / spanMeters;
            } else {
                computedScale = CustomChunkGenerator.ELEVATION_FACTOR;
            }

            double effectiveScale = Math.min(CustomChunkGenerator.ELEVATION_FACTOR, computedScale);

            CustomChunkGenerator.ELEVATION_MIN_METERS = minMeters;
            CustomChunkGenerator.ELEVATION_SCALE = effectiveScale;

            getBuildingMin(buildings, heightmap);
            placeDoors(buildings);
            placeLadders(buildings);
            placeChests(buildings);

            Map<Long, Integer> chestMap = new HashMap<>();
            for (BuildingPolygon bp : buildings) {
                if (bp == null) continue;
                if (bp.chestX != Integer.MIN_VALUE && bp.chestZ != Integer.MIN_VALUE && bp.chestY != Integer.MIN_VALUE) {
                    chestMap.put(Raster.keyFor(bp.chestX, bp.chestZ), bp.chestY);
                }
            }

            return new MapData(highwayWays, buildings, heightmap, roadMap, landuseMap, naturalMap, amenityMap, chestMap, railMap);

        } catch (Exception ex) {
            Bukkit.getLogger().severe("HighwayLoader Exception: " + ex.getMessage());
            ex.printStackTrace();
            return new MapData(Collections.emptyList(), Collections.emptyList(), new Heightmap(),
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyMap());
        }
    }

    private static void extractTags(Map<String, JsonObject> tagsByKey, Map<String, JsonArray> geomByKey, Map<String, JsonObject> elementByKey, Map<Long, Double> nodeLat, Map<Long, Double> nodeLon, Map<Long, String> naturalMap, Map<Long, String> amenityMap, Map<Long, String> landuseMap) {
        for (Map.Entry<String, JsonObject> entry : tagsByKey.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(":", 2);
            if (parts.length != 2) continue;
            String elType = parts[0];
            JsonObject tags = entry.getValue();

            String landuseVal = tags.has("landuse") ? tags.get("landuse").getAsString() : null;
            String leisureVal = tags.has("leisure") ? tags.get("leisure").getAsString() : null;
            String amenityVal = tags.has("amenity") ? tags.get("amenity").getAsString() : null;
            String naturalVal = tags.has("natural") ? tags.get("natural").getAsString() : null;

            if (landuseVal == null && leisureVal == null && amenityVal == null && naturalVal == null) continue;

            List<GpsPoint> polyPts = null;

            if (geomByKey.containsKey(key)) {
                polyPts = geometryToGpsPoints(geomByKey.get(key));
            } else {
                polyPts = getGpsPointList(elementByKey, key, nodeLat, nodeLon, polyPts, elType, geomByKey);
            }

            if (polyPts == null || polyPts.size() < 3) continue;

            int n = polyPts.size();
            int[] xs = new int[n];
            int[] zs = new int[n];
            for (int i = 0; i < n; i++) {
                GpsPoint gp = polyPts.get(i);
                xs[i] = Raster.lonToBlockX(gp.lon, gp.lat);
                zs[i] = Raster.latToBlockZ(gp.lat);
            }

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < n; i++) {
                if (xs[i] < minX) minX = xs[i];
                if (xs[i] > maxX) maxX = xs[i];
                if (zs[i] < minZ) minZ = zs[i];
                if (zs[i] > maxZ) maxZ = zs[i];
            }

            Material buildingMat = randomBuildingMaterial();

            String roofShape = "flat";
            if (tags.has("roof:shape")) {
                roofShape = tags.get("roof:shape").getAsString();
            }

            String streetName = "empty";
            if (tags.has("addr:street")) {
                streetName = tags.get("addr:street").getAsString();
            }

            BuildingPolygon poly = new BuildingPolygon(polyPts, xs, zs, 0.0, buildingMat, roofShape, streetName);

            double computedValue = AllocateBuildingValues.setPriceValue(poly);
            double computedEarning = AllocateBuildingValues.setEarningPerSecond(poly);
            poly.setValue(computedValue);
            poly.setEarning(computedEarning);

            Integer level = null;
            if (tags.has("building:levels")) {
                level = parseIntSafe(tags.get("building:levels").getAsString());
            } else if (tags.has("levels")) {
                level = parseIntSafe(tags.get("levels").getAsString());
            } else if (tags.has("roof:levels")) {
                level = parseIntSafe(tags.get("roof:levels").getAsString());
            }
            if (level != null && level > 0) poly.explicitLevels = level;


            String storeVal = null;
            boolean storeAsNatural = false;
            if (landuseVal != null) storeVal = landuseVal;
            else if (leisureVal != null) storeVal = leisureVal;
            else if (amenityVal != null) storeVal = amenityVal;
            else if (naturalVal != null) {
                storeVal = naturalVal;
                storeAsNatural = true;
            }

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (poly.contains(x, z)) {
                        long k = Raster.keyFor(x, z);
                        if (storeAsNatural) {
                            naturalMap.put(k, storeVal);
                        } else {
                            boolean isAmenityOnly = (amenityVal != null && landuseVal == null && leisureVal == null && naturalVal == null);
                            if (isAmenityOnly) {
                                if (!landuseMap.containsKey(k)) {
                                    amenityMap.put(k, storeVal);
                                }
                            } else {
                                landuseMap.put(k, storeVal);
                                amenityMap.remove(k);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void extractJson(Map<String, JsonObject> tagsByKey, Map<String, JsonArray> geomByKey, Map<String, JsonObject> elementByKey,
            Map<Long, Double> nodeLat, Map<Long, Double> nodeLon, List<WayData> highwayWays,
            int explicitHeightCount, int estimatedHeightCount, List<BuildingPolygon> buildings, List<WayData> railwayWays) {
        for (Map.Entry<String, JsonObject> entry : tagsByKey.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(":", 2);
            if (parts.length != 2) continue;
            String type = parts[0];
            JsonObject tags = entry.getValue();

            if ("way".equals(type)) {
                List<GpsPoint> wayPoints = null;

                if (geomByKey.containsKey(key)) {
                    wayPoints = geometryToGpsPoints(geomByKey.get(key));
                } else {
                    JsonObject wayObj = elementByKey.get(key);
                    if (wayObj != null && wayObj.has("nodes")) {
                        JsonArray nodeIds = wayObj.getAsJsonArray("nodes");
                        List<GpsPoint> pts = new ArrayList<>();
                        for (JsonElement nidElem : nodeIds) {
                            long nid = nidElem.getAsJsonPrimitive().getAsLong();
                            Double lat = nodeLat.get(nid);
                            Double lon = nodeLon.get(nid);
                            if (lat != null && lon != null) pts.add(new GpsPoint(lat, lon, "vertex"));
                        }
                        if (pts.size() >= 2) wayPoints = pts;
                    }
                }

                if (wayPoints == null || wayPoints.size() < 2) continue;

                if (tags.has("highway")) {
                    String highway = tags.get("highway").getAsString();
                    int lanes = -1;
                    if (tags.has("lanes")) {
                        try {
                            String lanesStr = tags.get("lanes").getAsString().split(";")[0].trim();
                            lanes = Integer.parseInt(lanesStr);
                        } catch (Exception ignored) {
                        }
                    }
                    int half = computeHalfWidth(highway, lanes);
                    highwayWays.add(new WayData(wayPoints, half));
                }

                if (tags.has("railway")) {
                    String rv = tags.get("railway").getAsString();
                    java.util.Set<String> trackTypes = Set.of(
                            "rail", "tram", "light_rail", "subway",
                            "narrow_gauge", "monorail", "siding"
                    );
                    if (trackTypes.contains(rv)) {
                        int half = 1;
                        railwayWays.add(new WayData(wayPoints, half));
                    }
                }

                if (tags.has("building")) {
                    String roofShape = "flat";
                    if (tags.has("roof:shape")) {
                        roofShape = tags.get("roof:shape").getAsString();
                    }
                    int n = wayPoints.size();
                    if (n >= 3) {
                        int[] xs = new int[n];
                        int[] zs = new int[n];
                        for (int i = 0; i < n; i++) {
                            GpsPoint gp = wayPoints.get(i);
                            xs[i] = Raster.lonToBlockX(gp.lon, gp.lat);
                            zs[i] = Raster.latToBlockZ(gp.lat);
                        }
                        double heightMeters = parseBuildingHeightMeters(tags, xs, zs, METERS_PER_BLOCK);
                        if (hasExplicitHeightTag(tags)) explicitHeightCount++;
                        else estimatedHeightCount++;

                        Material buildingMat = randomBuildingMaterial();

                        String streetName = "empty";
                        if (tags.has("addr:street")) {
                            streetName = tags.get("addr:street").getAsString();
                        }

                        BuildingPolygon poly = new BuildingPolygon(wayPoints, xs, zs, heightMeters, buildingMat, roofShape, streetName);

                        double computedValue = AllocateBuildingValues.setPriceValue(poly);
                        double computedEarning = AllocateBuildingValues.setEarningPerSecond(poly);
                        poly.setValue(computedValue);
                        poly.setEarning(computedEarning);


                        Integer level = null;
                        if (tags.has("building:levels")) {
                            level = parseIntSafe(tags.get("building:levels").getAsString());
                        } else if (tags.has("levels")) {
                            level = parseIntSafe(tags.get("levels").getAsString());
                        } else if (tags.has("roof:levels")) {
                            level = parseIntSafe(tags.get("roof:levels").getAsString());
                        }
                        if (level != null && level > 0) poly.explicitLevels = level;

                        if (poly.explicitLevels > 0) {
                            double perLevelMeters = (FloorHieght + 1.0) * METERS_PER_BLOCK;
                            poly.heightMeters = poly.explicitLevels * perLevelMeters;
                        }

                        buildings.add(poly);
                    }
                }
            }

            if ("relation".equals(type) && tags.has("building")) {
                JsonObject relObj = elementByKey.get(key);
                if (relObj != null && relObj.has("members")) {
                    JsonArray members = relObj.getAsJsonArray("members");
                    List<GpsPoint> relPoints = new ArrayList<>();
                    for (JsonElement mEl : members) {
                        JsonObject member = mEl.getAsJsonObject();
                        String role = member.has("role") ? member.get("role").getAsString() : "";
                        String mtype = member.has("type") ? member.get("type").getAsString() : "";
                        long ref = member.has("ref") ? member.get("ref").getAsLong() : -1L;
                        if (!"outer".equals(role)) continue;
                        if (!"way".equals(mtype)) continue;
                        String wayKey = "way:" + ref;
                        if (geomByKey.containsKey(wayKey)) {
                            relPoints.addAll(geometryToGpsPoints(geomByKey.get(wayKey)));
                        } else {
                            JsonObject wayObj = elementByKey.get(wayKey);
                            if (wayObj != null && wayObj.has("nodes")) {
                                JsonArray nodeIds = wayObj.getAsJsonArray("nodes");
                                for (JsonElement nidElem : nodeIds) {
                                    long nid = nidElem.getAsJsonPrimitive().getAsLong();
                                    Double lat = nodeLat.get(nid);
                                    Double lon = nodeLon.get(nid);
                                    if (lat != null && lon != null) relPoints.add(new GpsPoint(lat, lon, "vertex"));
                                }
                            }
                        }
                    }
                    if (relPoints.size() >= 3) {
                        int n = relPoints.size();
                        int[] xs = new int[n];
                        int[] zs = new int[n];
                        for (int i = 0; i < n; i++) {
                            GpsPoint gp = relPoints.get(i);
                            xs[i] = Raster.lonToBlockX(gp.lon, gp.lat);
                            zs[i] = Raster.latToBlockZ(gp.lat);
                        }
                        double heightMeters = parseBuildingHeightMeters(tags, xs, zs, METERS_PER_BLOCK);
                        if (hasExplicitHeightTag(tags)) explicitHeightCount++;
                        else estimatedHeightCount++;

                        Material buildingMat = randomBuildingMaterial();

                        String roofShape = "flat";
                        if (tags.has("roof:shape")) {
                            roofShape = tags.get("roof:shape").getAsString();
                        }

                        String streetName = "empty";
                        if (tags.has("addr:street")) {
                            streetName = tags.get("addr:street").getAsString();
                        }

                        BuildingPolygon poly = new BuildingPolygon(relPoints, xs, zs, heightMeters, buildingMat, roofShape, streetName);

                        double computedValue = AllocateBuildingValues.setPriceValue(poly);
                        double computedEarning = AllocateBuildingValues.setEarningPerSecond(poly);
                        poly.setValue(computedValue);
                        poly.setEarning(computedEarning);

                        Integer level = null;
                        if (tags.has("building:levels")) {
                            level = parseIntSafe(tags.get("building:levels").getAsString());
                        } else if (tags.has("levels")) {
                            level = parseIntSafe(tags.get("levels").getAsString());
                        } else if (tags.has("roof:levels")) {
                            level = parseIntSafe(tags.get("roof:levels").getAsString());
                        }
                        if (level != null && level > 0) poly.explicitLevels = level;

                        if (poly.explicitLevels > 0) {
                            double perLevelMeters = (FloorHieght + 1.0) * METERS_PER_BLOCK;
                            poly.heightMeters = poly.explicitLevels * perLevelMeters;
                        }

                        buildings.add(poly);
                    }
                }
            }
        }
    }

    private static void getBuildingMin(List<BuildingPolygon> buildings, Heightmap heightmap) {
        if (buildings == null || buildings.isEmpty()) return;

        for (BuildingPolygon bp : buildings) {
            if (bp == null || bp.poly == null || bp.xs == null || bp.zs == null) continue;

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < bp.xs.length; i++) {
                if (bp.xs[i] < minX) minX = bp.xs[i];
                if (bp.xs[i] > maxX) maxX = bp.xs[i];
                if (bp.zs[i] < minZ) minZ = bp.zs[i];
                if (bp.zs[i] > maxZ) maxZ = bp.zs[i];
            }

            int maxH = Integer.MIN_VALUE;
            int minH = Integer.MAX_VALUE;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!bp.contains(x, z)) continue;
                    double lat = -(z) / 111320.0;
                    double lon = (x) / (111320.0 * Math.cos(Math.toRadians(lat)));
                    double hMeters = heightmap.getDouble(lat, lon);
                    int hBlocks = CustomChunkGenerator.metersToBlockY(hMeters);
                    if (hBlocks > maxH) maxH = hBlocks;
                    if (hBlocks < minH) minH = hBlocks;
                }
            }

            if (maxH == Integer.MIN_VALUE) {
                if (bp.coords != null && !bp.coords.isEmpty()) {
                    double sumLat = 0.0, sumLon = 0.0;
                    for (GpsPoint g : bp.coords) {
                        sumLat += g.lat;
                        sumLon += g.lon;
                    }
                    double centerLat = sumLat / bp.coords.size();
                    double centerLon = sumLon / bp.coords.size();
                    double centerMeters = heightmap.getDouble(centerLat, centerLon);
                    int centerBlocks = CustomChunkGenerator.metersToBlockY(centerMeters);
                    maxH = centerBlocks;
                    minH = centerBlocks;
                } else {
                    int defaultMeters = 164;
                    int defaultBlocks = CustomChunkGenerator.metersToBlockY(defaultMeters);
                    maxH = defaultBlocks;
                    minH = defaultBlocks;
                }
            }
            if (minH == Integer.MAX_VALUE) minH = maxH;

            maxH = Math.max(CustomChunkGenerator.MC_MIN_Y, Math.min(CustomChunkGenerator.WORLD_MAX_Y, maxH));
            minH = Math.max(CustomChunkGenerator.MC_MIN_Y, Math.min(CustomChunkGenerator.WORLD_MAX_Y, minH));

            bp.baseBlockY = maxH;
            bp.minBlockY = minH;
        }
    }

    private static List<GpsPoint> getGpsPointList(Map<String, JsonObject> elementByKey, String key, Map<Long, Double> nodeLat, Map<Long, Double> nodeLon, List<GpsPoint> polyPts, String elType, Map<String, JsonArray> geomByKey) {
        JsonObject elObj = elementByKey.get(key);
        if (elObj != null && elObj.has("nodes")) {
            JsonArray nodeIds = elObj.getAsJsonArray("nodes");
            List<GpsPoint> pts = new ArrayList<>();
            for (JsonElement nidElem : nodeIds) {
                long nid = nidElem.getAsJsonPrimitive().getAsLong();
                Double lat = nodeLat.get(nid);
                Double lon = nodeLon.get(nid);
                if (lat != null && lon != null) pts.add(new GpsPoint(lat, lon, "vertex"));
            }
            if (pts.size() >= 3) polyPts = pts;
        } else if ("relation".equals(elType) && elObj != null && elObj.has("members")) {
            JsonArray members = elObj.getAsJsonArray("members");
            List<GpsPoint> relPoints = new ArrayList<>();
            for (JsonElement mEl : members) {
                JsonObject member = mEl.getAsJsonObject();
                String role = member.has("role") ? member.get("role").getAsString() : "";
                String mtype = member.has("type") ? member.get("type").getAsString() : "";
                long ref = member.has("ref") ? member.get("ref").getAsLong() : -1L;
                if (!"outer".equals(role)) continue;
                if (!"way".equals(mtype)) continue;
                String wayKey = "way:" + ref;
                if (geomByKey.containsKey(wayKey)) {
                    relPoints.addAll(geometryToGpsPoints(geomByKey.get(wayKey)));
                } else {
                    JsonObject wayObj = elementByKey.get(wayKey);
                    if (wayObj != null && wayObj.has("nodes")) {
                        JsonArray nodeIds = wayObj.getAsJsonArray("nodes");
                        for (JsonElement nidElem : nodeIds) {
                            long nid = nidElem.getAsJsonPrimitive().getAsLong();
                            Double lat = nodeLat.get(nid);
                            Double lon = nodeLon.get(nid);
                            if (lat != null && lon != null) relPoints.add(new GpsPoint(lat, lon, "vertex"));
                        }
                    }
                }
            }
            if (relPoints.size() >= 3) polyPts = relPoints;
        }
        return polyPts;
    }

    public static List<GpsPoint> geometryToGpsPoints(JsonArray geom) {
        List<GpsPoint> pts = new ArrayList<>();
        for (JsonElement g : geom) {
            JsonObject p = g.getAsJsonObject();
            double lat = p.get("lat").getAsDouble();
            double lon = p.get("lon").getAsDouble();
            pts.add(new GpsPoint(lat, lon, "vertex"));
        }
        return pts;
    }

    public static boolean hasExplicitHeightTag(JsonObject tags) {
        if (tags == null) return false;
        return tags.has("height") || tags.has("building:height") || tags.has("roof:height")
                || tags.has("building:levels") || tags.has("roof:levels") || tags.has("levels");
    }

    public static int computeHalfWidth(String highway, int lanes) {
        if (lanes > 0) {
            int half = (int) Math.max(1, Math.ceil(lanes * BLOCKS_PER_LANE / 2.0));
            return half;
        } else {
            switch (highway) {
                case "motorway":
                    return 6;
                case "trunk":
                    return 4;
                case "primary":
                    return 3;
                case "secondary":
                    return 2;
                case "tertiary":
                    return 1;
                default:
                    return 1;
            }
        }
    }

    public static double parseBuildingHeightMeters(JsonObject tags, int[] xs, int[] zs, double metersPerBlock) {
        if (tags == null) {
            return buildingHeight * metersPerBlock;
        }

        if (tags.has("height")) {
            Double v = parseHeightString(tags.get("height").getAsString());
            if (v != null && v > 0.0) return v;
        }
        if (tags.has("building:height")) {
            Double v = parseHeightString(tags.get("building:height").getAsString());
            if (v != null && v > 0.0) return v;
        }
        if (tags.has("roof:height")) {
            Double v = parseHeightString(tags.get("roof:height").getAsString());
            if (v != null && v > 0.0) return v;
        }

        if (tags.has("building:levels")) {
            Integer L = parseIntSafe(tags.get("building:levels").getAsString());
            if (L != null && L > 0) return L * FloorHieght;
        }
        if (tags.has("roof:levels")) {
            Integer L = parseIntSafe(tags.get("roof:levels").getAsString());
            if (L != null && L > 0) return L * FloorHieght;
        }
        if (tags.has("levels")) {
            Integer L = parseIntSafe(tags.get("levels").getAsString());
            if (L != null && L > 0) return L * FloorHieght;
        }

        return buildingHeight * metersPerBlock;
    }

    public static Double parseHeightString(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        s = s.replaceAll("\\s+", "");
        try {
            if (s.endsWith("cm")) {
                String num = s.substring(0, s.length() - 2).replace(',', '.');
                return Double.parseDouble(num) / 100.0;
            }
            if (s.endsWith("m")) {
                String num = s.substring(0, s.length() - 1).replace(',', '.');
                return Double.parseDouble(num);
            }
            if (s.endsWith("ft")) {
                String num = s.substring(0, s.length() - 2).replace(',', '.');
                return Double.parseDouble(num) * 0.3048;
            }
            if (s.endsWith("'")) {
                String num = s.substring(0, s.length() - 1).replace(',', '.');
                return Double.parseDouble(num) * 0.3048;
            }
            String normalized = s.replace(',', '.').replaceAll("[^0-9.\\-+eE]", "");
            if (normalized.isEmpty()) return null;
            return Double.parseDouble(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    public static Integer parseIntSafe(String s) {
        if (s == null) return null;
        try {
            String cleaned = s.replaceAll("[^0-9\\-+]", "");
            return Integer.parseInt(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    public static void placeDoors(List<BuildingPolygon> buildings) {
        if (buildings == null) return;
        int assigned = 0;
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        for (BuildingPolygon bp : buildings) {
            bp.doorX = Integer.MIN_VALUE;
            bp.doorZ = Integer.MIN_VALUE;
            bp.doorDir = -1;

            if (bp == null || bp.xs == null || bp.zs == null || bp.xs.length < 3) continue;

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < bp.xs.length; i++) {
                minX = Math.min(minX, bp.xs[i]);
                maxX = Math.max(maxX, bp.xs[i]);
                minZ = Math.min(minZ, bp.zs[i]);
                maxZ = Math.max(maxZ, bp.zs[i]);
            }

            List<int[]> candidates = new ArrayList<>();

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!bp.contains(x, z)) continue;
                    boolean isVertex = false;
                    for (int k = 0; k < bp.xs.length; k++) {
                        if (bp.xs[k] == x && bp.zs[k] == z) {
                            isVertex = true;
                            break;
                        }
                    }
                    if (isVertex) continue;
                    candidates.add(new int[]{x, z});
                }
            }

            if (candidates.isEmpty()) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!bp.contains(x, z)) continue;
                        if (!bp.contains(x, z - 1) || !bp.contains(x, z + 1) || !bp.contains(x - 1, z) || !bp.contains(x + 1, z)) {
                            candidates.add(new int[]{x, z});
                        }
                    }
                }
            }

            if (candidates.isEmpty()) continue;

            Collections.shuffle(candidates, rng);

            boolean placed = false;
            for (int[] cand : candidates) {
                int px = cand[0], pz = cand[1];

                List<Integer> validDirs = new ArrayList<>();

                java.util.function.BiPredicate<Integer, Integer> outsideFree = (ox, oz) -> {
                    for (BuildingPolygon other : buildings) {
                        if (other == null || other == bp) continue;
                        if (other.contains(ox, oz)) return false;
                    }
                    return true;
                };

                // N
                if (!bp.contains(px, pz - 1) && bp.contains(px, pz + 1)) {
                    int ox = px, oz = pz - 1;
                    if (outsideFree.test(ox, oz)) validDirs.add(0);
                }
                // E
                if (!bp.contains(px + 1, pz) && bp.contains(px - 1, pz)) {
                    int ox = px + 1, oz = pz;
                    if (outsideFree.test(ox, oz)) validDirs.add(1);
                }
                // S
                if (!bp.contains(px, pz + 1) && bp.contains(px, pz - 1)) {
                    int ox = px, oz = pz + 1;
                    if (outsideFree.test(ox, oz)) validDirs.add(2);
                }
                // W
                if (!bp.contains(px - 1, pz) && bp.contains(px + 1, pz)) {
                    int ox = px - 1, oz = pz;
                    if (outsideFree.test(ox, oz)) validDirs.add(3);
                }

                if (validDirs.isEmpty()) {
                    int[][] shifts = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] s : shifts) {
                        int sx = px + s[0], sz = pz + s[1];
                        if (!bp.contains(sx, sz)) continue;
                        if (!bp.contains(sx, sz - 1) && bp.contains(sx, sz + 1)) {
                            if (outsideFree.test(sx, sz - 1)) {
                                px = sx;
                                pz = sz;
                                validDirs.add(0);
                                break;
                            }
                        }
                        if (!bp.contains(sx + 1, sz) && bp.contains(sx - 1, sz)) {
                            if (outsideFree.test(sx + 1, sz)) {
                                px = sx;
                                pz = sz;
                                validDirs.add(1);
                                break;
                            }
                        }
                        if (!bp.contains(sx, sz + 1) && bp.contains(sx, sz - 1)) {
                            if (outsideFree.test(sx, sz + 1)) {
                                px = sx;
                                pz = sz;
                                validDirs.add(2);
                                break;
                            }
                        }
                        if (!bp.contains(sx - 1, sz) && bp.contains(sx + 1, sz)) {
                            if (outsideFree.test(sx - 1, sz)) {
                                px = sx;
                                pz = sz;
                                validDirs.add(3);
                                break;
                            }
                        }
                    }
                }

                if (!validDirs.isEmpty()) {
                    int dir = validDirs.get(rng.nextInt(validDirs.size()));
                    bp.doorX = px;
                    bp.doorZ = pz;
                    bp.doorDir = dir;
                    assigned++;
                    placed = true;
                    break;
                }
            }
        }
    }

    public static void placeLadders(List<BuildingPolygon> buildings) {
        if (buildings == null) return;
        int assigned = 0;
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        int[][] dirs = new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // 0=N,1=E,2=S,3=W

        for (BuildingPolygon bp : buildings) {
            bp.ladderX = Integer.MIN_VALUE;
            bp.ladderZ = Integer.MIN_VALUE;
            bp.ladderDir = -1;

            if (bp == null || bp.xs == null || bp.zs == null || bp.xs.length < 3) continue;

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < bp.xs.length; i++) {
                minX = Math.min(minX, bp.xs[i]);
                maxX = Math.max(maxX, bp.xs[i]);
                minZ = Math.min(minZ, bp.zs[i]);
                maxZ = Math.max(maxZ, bp.zs[i]);
            }

            java.util.List<int[]> wallCells = new java.util.ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!bp.contains(x, z)) continue;
                    boolean hasOutsideNeighbor = !bp.contains(x, z - 1) || !bp.contains(x, z + 1)
                            || !bp.contains(x - 1, z) || !bp.contains(x + 1, z);
                    if (hasOutsideNeighbor) wallCells.add(new int[]{x, z});
                }
            }

            if (wallCells.isEmpty()) continue;

            Collections.shuffle(wallCells, rng);

            boolean placed = false;
            for (int[] wc : wallCells) {
                int wx = wc[0], wz = wc[1];

                for (int d = 0; d < dirs.length; d++) {
                    int ox = wx + dirs[d][0];
                    int oz = wz + dirs[d][1];
                    if (bp.contains(ox, oz)) continue;

                    int ix = wx - dirs[d][0];
                    int iz = wz - dirs[d][1];

                    if (!bp.contains(ix, iz)) continue;

                    boolean isVertex = false;
                    for (int k = 0; k < bp.xs.length; k++) {
                        if (bp.xs[k] == ix && bp.zs[k] == iz) {
                            isVertex = true;
                            break;
                        }
                    }
                    if (isVertex) continue;

                    bp.ladderX = ix;
                    bp.ladderZ = iz;
                    bp.ladderDir = d;
                    assigned++;
                    placed = true;
                    break;
                }
                if (placed) break;
            }
        }

    }

    public static void placeChests(List<BuildingPolygon> buildings) {
        if (buildings == null) return;
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        int assigned = 0;

        double chestChancePerBuilding = 0.50;

        for (BuildingPolygon bp : buildings) {
            if (bp == null || bp.xs == null || bp.zs == null || bp.xs.length < 3) continue;

            if (rng.nextDouble() >= chestChancePerBuilding) continue;

            bp.chestX = Integer.MIN_VALUE;
            bp.chestZ = Integer.MIN_VALUE;
            bp.chestY = Integer.MIN_VALUE;

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < bp.xs.length; i++) {
                minX = Math.min(minX, bp.xs[i]);
                maxX = Math.max(maxX, bp.xs[i]);
                minZ = Math.min(minZ, bp.zs[i]);
                maxZ = Math.max(maxZ, bp.zs[i]);
            }

            java.util.List<int[]> candidates = new java.util.ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!bp.contains(x, z)) continue;
                    boolean isVertex = false;
                    for (int k = 0; k < bp.xs.length; k++) {
                        if (bp.xs[k] == x && bp.zs[k] == z) {
                            isVertex = true;
                            break;
                        }
                    }
                    if (isVertex) continue;
                    if (bp.doorX != Integer.MIN_VALUE && bp.doorZ != Integer.MIN_VALUE && bp.doorX == x && bp.doorZ == z)
                        continue;
                    if (bp.ladderX != Integer.MIN_VALUE && bp.ladderZ != Integer.MIN_VALUE && bp.ladderX == x && bp.ladderZ == z)
                        continue;
                    candidates.add(new int[]{x, z});
                }
            }

            if (candidates.isEmpty()) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!bp.contains(x, z)) continue;
                        boolean hasOutsideNeighbor = !bp.contains(x, z - 1) || !bp.contains(x, z + 1)
                                || !bp.contains(x - 1, z) || !bp.contains(x + 1, z);
                        if (hasOutsideNeighbor) {
                            if (bp.doorX != Integer.MIN_VALUE && bp.doorZ != Integer.MIN_VALUE && bp.doorX == x && bp.doorZ == z)
                                continue;
                            if (bp.ladderX != Integer.MIN_VALUE && bp.ladderZ != Integer.MIN_VALUE && bp.ladderX == x && bp.ladderZ == z)
                                continue;
                            candidates.add(new int[]{x, z});
                        }
                    }
                }
            }

            if (candidates.isEmpty()) continue;

            Collections.shuffle(candidates, rng);

            int[] sel = candidates.get(0);
            int cx = sel[0];
            int cz = sel[1];

            int chestY = Integer.MIN_VALUE;

            int blocksHigh = Math.max(1, (int) Math.round(bp.heightMeters / METERS_PER_BLOCK));
            java.util.Set<Integer> floorYs = new java.util.HashSet<>();
            if (bp.explicitLevels > 0) {
                int levels = bp.explicitLevels;
                int spacing = Math.max((int) Math.round(FloorHieght) + 1,
                        (int) Math.round((double) blocksHigh / Math.max(1, levels)));
                for (int f = 1; f < levels; f++) {
                    int fy = bp.baseBlockY + f * spacing;
                    if (fy > bp.baseBlockY + 2 && fy < bp.baseBlockY + blocksHigh) floorYs.add(fy);
                }
            } else {
                if (blocksHigh >= 8) {
                    for (int fy = bp.baseBlockY + 4; fy < bp.baseBlockY + blocksHigh; fy += 4) {
                        if (fy > bp.baseBlockY + 2) floorYs.add(fy);
                    }
                }
            }

            for (Integer fy : floorYs) {
                int cy = fy + 1;
                if (cy > 0 && cy <= CustomChunkGenerator.WORLD_MAX_Y) {
                    chestY = cy;
                    break;
                }
            }

            if (chestY == Integer.MIN_VALUE) {
                if (bp.baseBlockY >= 0) chestY = bp.baseBlockY + 1;
                else if (bp.minBlockY >= 0) chestY = bp.minBlockY + 1;
                else chestY = Math.max(1, Math.min(CustomChunkGenerator.WORLD_MAX_Y, 64)); // generic fallback
            }

            chestY = Math.max(1, Math.min(CustomChunkGenerator.WORLD_MAX_Y, chestY));

            bp.chestX = cx;
            bp.chestZ = cz;
            bp.chestY = chestY;
            assigned++;
        }
    }

    public static Material randomBuildingMaterial() {
        Random rnd = new Random();

        List<Material> variants = Arrays.asList(
                Material.WHITE_CONCRETE,
                Material.STONE_BRICKS,
                Material.GRAY_CONCRETE,
                Material.SMOOTH_STONE,
                Material.POLISHED_ANDESITE,
                Material.BRICKS,
                Material.LIGHT_GRAY_CONCRETE
        );

        return variants.get(rnd.nextInt(variants.size()));
    }
}