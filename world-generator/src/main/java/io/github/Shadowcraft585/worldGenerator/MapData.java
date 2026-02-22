package io.github.Shadowcraft585.worldGenerator;

import java.util.List;
import java.util.Map;

public class MapData {

    public List<WayData> highwayWays;
    public List<BuildingPolygon> buildings;
    public Heightmap heightmap;
    public Map<Long, String> roadBlocks;
    public Map<Long, String> landuseMap;
    public Map<Long, String> naturalMap;
    public Map<Long, String> amenityMap;
    public Map<Long, Integer> chestMap;
    public Map<Long, String> railBlocks;

    public MapData(
            List<WayData> highwayWays,
            List<BuildingPolygon> buildings,
            Heightmap heightmap,
            Map<Long, String> roadBlocks,
            Map<Long, String> landuseMap,
            Map<Long, String> naturalMap,
            Map<Long, String> amenityMap,
            Map<Long, Integer> chestMap,
            Map<Long, String> railBlocks
    ) {
        this.highwayWays = highwayWays;
        this.buildings = buildings;
        this.heightmap = heightmap;
        this.roadBlocks = roadBlocks;
        this.landuseMap = landuseMap;
        this.naturalMap = naturalMap;
        this.amenityMap = amenityMap;
        this.chestMap = chestMap;
        this.railBlocks = railBlocks;
    }
}