package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

public class LanduseMaterial {

    public static Map<String, Material> MAP = new HashMap<>();

    static {
        MAP.put("residential", Material.DEEPSLATE_BRICKS);
        MAP.put("commercial", Material.DEEPSLATE_BRICKS);
        MAP.put("industrial", Material.DEEPSLATE_BRICKS);
        MAP.put("retail", Material.STONE_BRICKS);
        MAP.put("forest", Material.GRASS_BLOCK);
        MAP.put("meadow", Material.GRASS_BLOCK);
        MAP.put("grass", Material.GRASS_BLOCK);
        MAP.put("park", Material.GRASS_BLOCK);
        MAP.put("cemetery", Material.ROOTED_DIRT);
        MAP.put("recreation_ground", Material.GRASS_BLOCK);
        MAP.put("allotments", Material.COARSE_DIRT);
        MAP.put("farmland", Material.COARSE_DIRT);
        MAP.put("farmyard", Material.COARSE_DIRT);
        MAP.put("vineyard", Material.COARSE_DIRT);
        MAP.put("orchard", Material.COARSE_DIRT);
        MAP.put("quarry", Material.COBBLESTONE);
        MAP.put("basin", Material.WATER);
        MAP.put("reservoir", Material.WATER);
        MAP.put("water", Material.WATER);

        MAP.put("park", Material.GRASS_BLOCK);
        MAP.put("swimming_pool", Material.WATER);
        MAP.put("marina", Material.WATER);

        MAP.put("village_green", Material.GRASS_BLOCK);
        MAP.put("pitch", Material.GRASS_BLOCK);
        MAP.put("water", Material.WATER);
        MAP.put("wood", Material.GRASS_BLOCK);
        MAP.put("forest", Material.GRASS_BLOCK);
        MAP.put("grassland", Material.GRASS_BLOCK);
        MAP.put("heath", Material.GRASS_BLOCK);
        MAP.put("scrub", Material.GRASS_BLOCK);
        MAP.put("wetland", Material.MUDDY_MANGROVE_ROOTS);
        MAP.put("marsh", Material.WATER);
        MAP.put("beach", Material.SAND);
        MAP.put("bare_rock", Material.STONE);
        MAP.put("scree", Material.GRAVEL);

        MAP.put("parking", Material.DEEPSLATE_BRICKS);
        MAP.put("parking_space", Material.DEEPSLATE_BRICKS);
    }

    public static Material materialFor(String tagKey, String tagValue) {
        if (tagValue == null) return Material.GRASS_BLOCK;

        Material m = MAP.get(tagValue);
        if (m != null) return m;

        m = MAP.get(tagKey + ":" + tagValue);
        if (m != null) return m;

        switch (tagKey) {
            case "landuse":
                return Material.GRASS_BLOCK;
            case "natural":
                return Material.GRASS_BLOCK;
            case "leisure":
                return Material.GRASS_BLOCK;
            default:
                return Material.GRASS_BLOCK;
        }
    }
}