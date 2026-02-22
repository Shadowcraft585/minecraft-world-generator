package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.jetbrains.annotations.NotNull;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.BlockFace;

import java.util.Map;
import java.util.Random;

public class CustomChunkGenerator extends ChunkGenerator {

    public MapData mapData;

    public static double METERS_PER_BLOCK = 1.0;
    public static double ELEVATION_FACTOR = 2.0;

    public static double ELEVATION_MIN_METERS = 0.0;
    public static double ELEVATION_SCALE = ELEVATION_FACTOR;

    public static final int MC_MIN_Y = -64;
    public static final int MC_TARGET_MAX_Y = 320;

    public static final int WORLD_MAX_Y = 500;

    public CustomChunkGenerator(MapData mapData) {
        this.mapData = mapData;
        String elevationFactor = XMLReader.getVariable("CustomChunkGenerator", "elevationFactor");
        ELEVATION_FACTOR = Double.parseDouble(elevationFactor);

    }

    public static int metersToBlockY(double meters) {
        double blocks = (meters - ELEVATION_MIN_METERS) * ELEVATION_SCALE + MC_MIN_Y;
        int y = (int) Math.round(blocks);
        y = Math.max(MC_MIN_Y, Math.min(WORLD_MAX_Y, y));
        return y;
    }

    @Override
    public void generateNoise(
            @NotNull WorldInfo worldInfo,
            @NotNull Random random,
            int chunkX,
            int chunkZ,
            @NotNull ChunkData chunk
    ) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;

        Map<Long, String> roadMap = (mapData != null) ? mapData.roadBlocks : null;
        Map<Long, String> landuseMap = (mapData != null) ? mapData.landuseMap : null;
        Map<Long, String> naturalMap = (mapData != null) ? mapData.naturalMap : null;
        Map<Long, String> amenityMap = (mapData != null) ? mapData.amenityMap : null;

        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ (((long) chunkX) << 32) ^ (chunkZ * 31L));

        boolean[][] treePlaced = new boolean[16][16];

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int wx = baseX + dx;
                int wz = baseZ + dz;

                double lat = -(wz) / 111320.0;
                double lon = (wx) / (111320.0 * Math.cos(Math.toRadians(lat)));

                double rawMeters = 164.0;
                if (mapData != null && mapData.heightmap != null) {
                    rawMeters = mapData.heightmap.getDouble(lat, lon);
                }

                int height = metersToBlockY(rawMeters);

                int bedrockY = MC_MIN_Y;
                try {
                    chunk.setBlock(dx, bedrockY, dz, Material.BEDROCK);
                } catch (Throwable ignored) {}

                int startFillY = MC_MIN_Y + 1;
                for (int fy = startFillY; fy < height; fy++) {
                    if (fy < height - 1) {
                        chunk.setBlock(dx, fy, dz, Material.STONE);
                    } else {
                        chunk.setBlock(dx, fy, dz, Material.DIRT);
                    }
                }

                long key = Raster.keyFor(wx, wz);

                if (mapData != null && mapData.buildings != null) {
                    if (handleBuildingAt(chunk, dx, dz, wx, wz, height)) continue;
                    //if (handleDecrepitBuildingAt(chunk, dx, dz, wx, wz, height)) continue;
                }

                if (roadMap != null && roadMap.containsKey(key)) {
                    double min = 0.0;
                    double max = 1.0;
                    double chance = min + (max - min) * random.nextDouble();
                    if (chance < 0.05) chunk.setBlock(dx, height, dz, Material.COBWEB);
                    else if (chance < 0.15) chunk.setBlock(dx, height, dz, Material.BLACKSTONE);
                    else if (chance < 0.25) chunk.setBlock(dx, height, dz, Material.BASALT);
                    else if (chance < 0.3) chunk.setBlock(dx, height, dz, Material.AIR);
                    else chunk.setBlock(dx, height, dz, Material.GRAY_CONCRETE);
                    continue;
                }

                Material surfaceMaterial = null;
                if (amenityMap != null && amenityMap.containsKey(key)) {
                    surfaceMaterial = LanduseMaterial.materialFor("amenity", amenityMap.get(key));
                    applySurface(chunk, dx, dz, height, surfaceMaterial);
                } else if (naturalMap != null && naturalMap.containsKey(key)) {
                    surfaceMaterial = LanduseMaterial.materialFor("natural", naturalMap.get(key));
                    applySurface(chunk, dx, dz, height, surfaceMaterial);
                } else if (landuseMap != null && landuseMap.containsKey(key)) {
                    surfaceMaterial = LanduseMaterial.materialFor("landuse", landuseMap.get(key));
                    applySurface(chunk, dx, dz, height, surfaceMaterial);
                } else {
                    surfaceMaterial = Material.GRASS_BLOCK;
                    chunk.setBlock(dx, height, dz, surfaceMaterial);
                }

                if (mapData != null && mapData.railBlocks != null && mapData.railBlocks.containsKey(key)) {
                    placeRailAt(chunk, dx, dz, height, chunkRng);
                    continue;
                }

                String landuseTag = (landuseMap != null) ? landuseMap.get(key) : null;
                String naturalTag = (naturalMap != null) ? naturalMap.get(key) : null;



                generateFlora(chunk, dx, dz, wx, wz, height, landuseTag, naturalTag, seed, treePlaced, surfaceMaterial);
            }
        }
    }

    private boolean handleBuildingAt(ChunkData chunk, int dx, int dz, int wx, int wz, int terrainHeight) {
        if (mapData == null || mapData.buildings == null) return false;
        BuildingPolygon current = null;
        for (BuildingPolygon bp : mapData.buildings) {
            if (bp != null && bp.contains(wx, wz)) {
                current = bp;
                break;
            }
        }
        if (current == null) return false;

        double buildingHeight = current.heightMeters;
        int blocksHigh = Math.max(1, (int) Math.round(buildingHeight / METERS_PER_BLOCK));

        if (current != null && current.explicitLevels <= 0) {
            String landuseTags = null;
            if (mapData != null) {
                long keyHere = Raster.keyFor(wx, wz);
                if (mapData.landuseMap != null) landuseTags = mapData.landuseMap.get(keyHere);
                if (landuseTags == null && mapData.naturalMap != null) landuseTags = mapData.naturalMap.get(keyHere);
                if (landuseTags == null && mapData.amenityMap != null) landuseTags = mapData.amenityMap.get(keyHere);
            }

            if (landuseTags != null) {
                if ("allotments".equals(landuseTags) ||
                        "farmland".equals(landuseTags) ||
                        "farmyard".equals(landuseTags) ||
                        "vineyard".equals(landuseTags) ||
                        "orchard".equals(landuseTags) ||
                        "cemetery".equals(landuseTags) ||
                        "recreation_ground".equals(landuseTags) ||
                        "retail".equals(landuseTags) ||
                        "meadow".equals(landuseTags) ||
                        "forest".equals(landuseTags) ||
                        "grass".equals(landuseTags) ||
                        "park".equals(landuseTags)) {
                    int smallHeight = (int) Math.round(4 * METERS_PER_BLOCK);
                    blocksHigh = smallHeight;
                }
            }
        }

        int baseY = (current.baseBlockY >= CustomChunkGenerator.MC_MIN_Y) ? current.baseBlockY : terrainHeight;
        int minY  = (current.minBlockY  >= CustomChunkGenerator.MC_MIN_Y) ? current.minBlockY  : terrainHeight;

        minY = Math.min(minY, terrainHeight);

        baseY = Math.max(CustomChunkGenerator.MC_MIN_Y, Math.min(CustomChunkGenerator.WORLD_MAX_Y, baseY));
        minY  = Math.max(CustomChunkGenerator.MC_MIN_Y, Math.min(CustomChunkGenerator.WORLD_MAX_Y, minY));

        baseY = Math.max(MC_MIN_Y, Math.min(WORLD_MAX_Y, baseY));
        minY = Math.max(MC_MIN_Y, Math.min(WORLD_MAX_Y, minY));

        Material foundation = current.buildingMaterial;
        for (int y = minY; y <= baseY; y++) {
            chunk.setBlock(dx, y, dz, foundation);
        }

        int roofY = Math.min(WORLD_MAX_Y, baseY + blocksHigh);

        java.util.Set<Integer> floorYs = new java.util.HashSet<>();
        if (current.explicitLevels > 0) {
            int levels = current.explicitLevels;
            int spacing = Math.max((int) Math.round(HighwayLoader.FloorHieght) + 1,
                    (int) Math.round((double) blocksHigh / Math.max(1, levels)));
            for (int f = 1; f < levels; f++) {
                int fy = baseY + f * spacing;
                if (fy > baseY + 2 && fy < roofY) floorYs.add(fy);
            }
        } else {
            if (blocksHigh >= 8) {
                for (int fy = baseY + 4; fy < roofY; fy += 4) {
                    if (fy > baseY + 2) floorYs.add(fy);
                }
            }
        }

        Material floorMat = Material.OAK_PLANKS;

        chunk.setBlock(dx, baseY, dz, current.buildingMaterial);

        Ladder ladderData = (Ladder) Material.LADDER.createBlockData();

        boolean isLadderColumn = (current.ladderX != Integer.MIN_VALUE && current.ladderZ != Integer.MIN_VALUE
                && current.ladderX == wx && current.ladderZ == wz);

        BlockFace ladderFacing = null;
        if (isLadderColumn) {
            switch (current.ladderDir) {
                case 0: ladderFacing = BlockFace.NORTH; break;
                case 1: ladderFacing = BlockFace.EAST; break;
                case 2: ladderFacing = BlockFace.SOUTH; break;
                case 3: ladderFacing = BlockFace.WEST; break;
                default: ladderFacing = BlockFace.NORTH; break;
            }
            try {
                ladderData.setFacing(ladderFacing);
            } catch (Throwable ignored) { }

            int attachX = wx, attachZ = wz;
            switch (ladderFacing) {
                case NORTH -> attachZ = wz - 1;
                case EAST  -> attachX = wx + 1;
                case SOUTH -> attachZ = wz + 1;
                case WEST  -> attachX = wx - 1;
                default -> { }
            }

            boolean attachInside = current.contains(attachX, attachZ);
            boolean attachIsWall = false;
            if (attachInside) {
                boolean e = current.contains(attachX + 1, attachZ);
                boolean w = current.contains(attachX - 1, attachZ);
                boolean s = current.contains(attachX, attachZ + 1);
                boolean n = current.contains(attachX, attachZ - 1);
                attachIsWall = !(e && w && s && n);
            }

            if (!(attachInside && attachIsWall)) {
                isLadderColumn = false;
            }
        }

        for (int y = baseY + 1; y <= roofY; y++) {
            boolean isRoof = (y == roofY);

            boolean isWall = true;
            if (current != null && current.poly != null) {
                boolean east = current.contains(wx + 1, wz);
                boolean west = current.contains(wx - 1, wz);
                boolean south = current.contains(wx, wz + 1);
                boolean north = current.contains(wx, wz - 1);
                isWall = !(east && west && south && north);
            }

            boolean isDoorColumn = (current != null
                    && current.doorX != Integer.MIN_VALUE
                    && current.doorZ != Integer.MIN_VALUE
                    && current.doorX == wx && current.doorZ == wz);

            if (isRoof) {
                chunk.setBlock(dx, y, dz, current.buildingMaterial);
            } else if (floorYs.contains(y) && !isWall) {
                if (isLadderColumn) {
                    try {
                        chunk.setBlock(dx, y, dz, ladderData);
                    } catch (Throwable t) {
                        chunk.setBlock(dx, y, dz, Material.LADDER);
                    }
                } else {
                    chunk.setBlock(dx, y, dz, floorMat);
                }
            } else if (isWall) {
                if (isDoorColumn && (y == baseY + 1 || y == baseY + 2)) {
                    chunk.setBlock(dx, y, dz, Material.AIR);
                } else {
                    chunk.setBlock(dx, y, dz, current.buildingMaterial);
                }
            } else {
                if (isLadderColumn) {
                    try {
                        chunk.setBlock(dx, y, dz, ladderData);
                    } catch (Throwable t) {
                        chunk.setBlock(dx, y, dz, Material.LADDER);
                    }
                } else {
                    chunk.setBlock(dx, y, dz, Material.AIR);
                }
            }
        }

        int extra = computeRoof(current, wx, wz, blocksHigh);
        for (int e = 1; e <= extra; e++) {
            int ty = roofY + e;
            if (ty > WORLD_MAX_Y) break;
            try {
                chunk.setBlock(dx, ty, dz, current.buildingMaterial);
            } catch (Throwable ignored) { }
        }

        return true;
    }

    private boolean handleDecrepitBuildingAt(ChunkData chunk, int dx, int dz, int wx, int wz, int terrainHeight) {
        final double MIN = 0.0;
        final double MAX = 1.0;

        if (mapData == null || mapData.buildings == null) return false;
        BuildingPolygon current = null;
        for (BuildingPolygon bp : mapData.buildings) {
            if (bp != null && bp.contains(wx, wz)) {
                current = bp;
                break;
            }
        }
        if (current == null) return false;

        long seedHash = java.util.Objects.hash(
                wx, wz,
                java.util.Arrays.hashCode(current.xs),
                java.util.Arrays.hashCode(current.zs)
        );
        Random random = new Random(seedHash);

        double buildingHeight = current.heightMeters;
        int blocksHigh = Math.max(1, (int) Math.round(buildingHeight / METERS_PER_BLOCK));

        if (current != null && current.explicitLevels <= 0) {
            String landuseTags = null;
            if (mapData != null) {
                long keyHere = Raster.keyFor(wx, wz);
                if (mapData.landuseMap != null) landuseTags = mapData.landuseMap.get(keyHere);
                if (landuseTags == null && mapData.naturalMap != null) landuseTags = mapData.naturalMap.get(keyHere);
                if (landuseTags == null && mapData.amenityMap != null) landuseTags = mapData.amenityMap.get(keyHere);
            }

            if (landuseTags != null) {
                if ("allotments".equals(landuseTags) ||
                        "farmland".equals(landuseTags) ||
                        "farmyard".equals(landuseTags) ||
                        "vineyard".equals(landuseTags) ||
                        "orchard".equals(landuseTags) ||
                        "cemetery".equals(landuseTags) ||
                        "recreation_ground".equals(landuseTags) ||
                        "retail".equals(landuseTags) ||
                        "meadow".equals(landuseTags) ||
                        "forest".equals(landuseTags) ||
                        "grass".equals(landuseTags) ||
                        "park".equals(landuseTags)) {
                    int smallHeight = (int) Math.round(4 * METERS_PER_BLOCK);
                    blocksHigh = smallHeight;
                }
            }
        }

        int baseY = (current.baseBlockY >= CustomChunkGenerator.MC_MIN_Y) ? current.baseBlockY : terrainHeight;
        int minY  = (current.minBlockY  >= CustomChunkGenerator.MC_MIN_Y) ? current.minBlockY  : terrainHeight;

        minY = Math.min(minY, terrainHeight);

        baseY = Math.max(CustomChunkGenerator.MC_MIN_Y, Math.min(CustomChunkGenerator.WORLD_MAX_Y, baseY));
        minY  = Math.max(CustomChunkGenerator.MC_MIN_Y, Math.min(CustomChunkGenerator.WORLD_MAX_Y, minY));

        baseY = Math.max(MC_MIN_Y, Math.min(WORLD_MAX_Y, baseY));
        minY = Math.max(MC_MIN_Y, Math.min(WORLD_MAX_Y, minY));

        Material foundation = Material.STONE_BRICKS;
        for (int y = minY; y <= baseY; y++) {
            double c = MIN + (MAX - MIN) * random.nextDouble();
            if (c < 0.05) foundation = Material.COBWEB;
            else if (c < 0.20) foundation = Material.COBBLESTONE;
            else if (c < 0.40) foundation = Material.MOSSY_COBBLESTONE;
            else if (c < 0.45) foundation = Material.AIR;
            else foundation = current.buildingMaterial;
            chunk.setBlock(dx, y, dz, foundation);
        }

        int roofY = Math.min(WORLD_MAX_Y, baseY + blocksHigh);

        java.util.Set<Integer> floorYs = new java.util.HashSet<>();
        if (current.explicitLevels > 0) {
            int levels = current.explicitLevels;
            int spacing = Math.max((int) Math.round(HighwayLoader.FloorHieght) + 1,
                    (int) Math.round((double) blocksHigh / Math.max(1, levels)));
            for (int f = 1; f < levels; f++) {
                int fy = baseY + f * spacing;
                if (fy > baseY + 2 && fy < roofY) floorYs.add(fy);
            }
        } else {
            if (blocksHigh >= 8) {
                for (int fy = baseY + 4; fy < roofY; fy += 4) {
                    if (fy > baseY + 2) floorYs.add(fy);
                }
            }
        }

        double chanceBase = MIN + (MAX - MIN) * random.nextDouble();
        if (chanceBase < 0.05) {
            chunk.setBlock(dx, baseY, dz, Material.COBWEB);
        } else if (chanceBase < 0.10) {
            chunk.setBlock(dx, baseY, dz, Material.AIR);
        } else if (chanceBase < 0.20) {
            chunk.setBlock(dx, baseY, dz, Material.COBBLESTONE);
        } else if (chanceBase < 0.30) {
            chunk.setBlock(dx, baseY, dz, Material.MOSSY_COBBLESTONE);
        } else {
            chunk.setBlock(dx, baseY, dz, current.buildingMaterial);
        }

        Ladder ladderData = (Ladder) Material.LADDER.createBlockData();

        boolean isLadderColumn = (current.ladderX != Integer.MIN_VALUE && current.ladderZ != Integer.MIN_VALUE
                && current.ladderX == wx && current.ladderZ == wz);

        BlockFace ladderFacing = null;
        if (isLadderColumn) {
            switch (current.ladderDir) {
                case 0: ladderFacing = BlockFace.NORTH; break;
                case 1: ladderFacing = BlockFace.EAST; break;
                case 2: ladderFacing = BlockFace.SOUTH; break;
                case 3: ladderFacing = BlockFace.WEST; break;
                default: ladderFacing = BlockFace.NORTH; break;
            }
            try {
                ladderData.setFacing(ladderFacing);
            } catch (Throwable ignored) { }
            int attachX = wx, attachZ = wz;
            switch (ladderFacing) {
                case NORTH -> attachZ = wz - 1;
                case EAST  -> attachX = wx + 1;
                case SOUTH -> attachZ = wz + 1;
                case WEST  -> attachX = wx - 1;
                default -> { }
            }

            boolean attachInside = current.contains(attachX, attachZ);
            boolean attachIsWall = false;
            if (attachInside) {
                boolean e = current.contains(attachX + 1, attachZ);
                boolean w = current.contains(attachX - 1, attachZ);
                boolean s = current.contains(attachX, attachZ + 1);
                boolean n = current.contains(attachX, attachZ - 1);
                attachIsWall = !(e && w && s && n);
            }
            if (!(attachInside && attachIsWall)) isLadderColumn = false;
        }

        boolean chestWanted = (current.chestX != Integer.MIN_VALUE && current.chestZ != Integer.MIN_VALUE
                && current.chestX == wx && current.chestZ == wz);

        if (chestWanted && current.chestY == Integer.MIN_VALUE) {
            java.util.List<Integer> candidates = new java.util.ArrayList<>();

            for (Integer fy : floorYs) {
                int cy = fy + 1;
                if (cy > baseY && cy < roofY) candidates.add(cy);
            }

            int baseChestY = baseY + 1;
            if (baseChestY > baseY && baseChestY < roofY) candidates.add(baseChestY);

            if (candidates.isEmpty()) {
                for (int yy = baseY + 1; yy < roofY; yy++) {
                    if (yy - 1 == baseY || floorYs.contains(yy - 1)) {
                        candidates.add(yy);
                    }
                }
            }

            if (candidates.isEmpty()) candidates.add(Math.min(baseY + 1, roofY - 1));

            int idx = Math.abs((int) (seedHash ^ 0x9e3779b97f4a7c15L)) % candidates.size();
            current.chestY = candidates.get(idx);
        }

        for (int y = baseY + 1; y <= roofY; y++) {
            double c = MIN + (MAX - MIN) * random.nextDouble();
            Material floorMat = (c < 0.05) ? Material.COBWEB : (c < 0.10) ? Material.AIR : Material.OAK_PLANKS;
            boolean isRoof = (y == roofY);

            boolean isWall = true;
            if (current != null && current.poly != null) {
                boolean east = current.contains(wx + 1, wz);
                boolean west = current.contains(wx - 1, wz);
                boolean south = current.contains(wx, wz + 1);
                boolean north = current.contains(wx, wz - 1);
                isWall = !(east && west && south && north);
            }

            boolean isDoorColumn = (current != null
                    && current.doorX != Integer.MIN_VALUE
                    && current.doorZ != Integer.MIN_VALUE
                    && current.doorX == wx && current.doorZ == wz);

            if (isRoof) {
                double roofChance = MIN + (MAX - MIN) * random.nextDouble();
                if (roofChance < 0.05) chunk.setBlock(dx, y, dz, Material.COBWEB);
                else if (roofChance < 0.15) chunk.setBlock(dx, y, dz, Material.COBBLESTONE);
                else if (roofChance < 0.25) chunk.setBlock(dx, y, dz, Material.MOSSY_COBBLESTONE);
                else if (roofChance < 0.35) chunk.setBlock(dx, y, dz, Material.AIR);
                else chunk.setBlock(dx, y, dz, current.buildingMaterial);
                continue;
            }

            if (floorYs.contains(y) && !isWall) {
                if (isLadderColumn) {
                    try { chunk.setBlock(dx, y, dz, ladderData); }
                    catch (Throwable t) { chunk.setBlock(dx, y, dz, Material.LADDER); }
                } else {
                    chunk.setBlock(dx, y, dz, floorMat);
                }

                if (chestWanted && current.chestY == y && !isDoorColumn && !isLadderColumn) {
                    chunk.setBlock(dx, y, dz, Material.CHEST);
                }
                continue;
            }

            if (isWall) {
                if (isDoorColumn && (y == baseY + 1 || y == baseY + 2)) {
                    chunk.setBlock(dx, y, dz, Material.AIR);
                } else {
                    double wallChance = MIN + (MAX - MIN) * random.nextDouble();
                    if (wallChance < 0.05) chunk.setBlock(dx, y, dz, Material.COBWEB);
                    else if (wallChance < 0.15) chunk.setBlock(dx, y, dz, Material.COBBLESTONE);
                    else if (wallChance < 0.25) chunk.setBlock(dx, y, dz, Material.MOSSY_COBBLESTONE);
                    else if (wallChance < 0.35) chunk.setBlock(dx, y, dz, Material.AIR);
                    else chunk.setBlock(dx, y, dz, current.buildingMaterial);
                }
                continue;
            }

            if (chestWanted && current.chestY == y && !isDoorColumn && !isLadderColumn) {
                chunk.setBlock(dx, y, dz, Material.CHEST);
                continue;
            }

            if (isLadderColumn) {
                try { chunk.setBlock(dx, y, dz, ladderData); }
                catch (Throwable t) { chunk.setBlock(dx, y, dz, Material.LADDER); }
            } else {
                chunk.setBlock(dx, y, dz, Material.AIR);
            }
        }

        int extra = computeRoof(current, wx, wz, blocksHigh);
        for (int e = 1; e <= extra; e++) {
            int ty = roofY + e;
            if (ty > WORLD_MAX_Y) break;
            try {
                chunk.setBlock(dx, ty, dz, current.buildingMaterial);
            } catch (Throwable ignored) { }
        }

        return true;
    }

    private void generateFlora(
            ChunkData chunk,
            int dx,
            int dz,
            int wx,
            int wz,
            int groundY,
            String landuseTag,
            String naturalTag,
            long worldSeed,
            boolean[][] treePlaced,
            Material surfaceMaterial
    ) {
        if (groundY <= MC_MIN_Y || groundY >= WORLD_MAX_Y) return;

        String tag = (landuseTag != null) ? landuseTag : naturalTag;
        if (tag == null) return;

        long cellSeed = worldSeed + ((long) wx) * 341873128712L + ((long) wz) * 132897987541L;
        Random rnd = new Random(cellSeed);

        if ("allotments".equals(tag) || "farmland".equals(tag) || "farmyard".equals(tag)
                || "vineyard".equals(tag) || "orchard".equals(tag)) {
            if (rnd.nextDouble() < 0.08) {
                int y = Math.min(WORLD_MAX_Y, groundY + 1);
                if (surfaceMaterial == Material.COARSE_DIRT || surfaceMaterial == Material.GRASS_BLOCK) {
                    chunk.setBlock(dx, y, dz, Material.DEAD_BUSH);
                }
                return;
            }
            return;
        }

        if ("recreation_ground".equals(tag) || "park".equals(tag)) {
            if (surfaceMaterial == Material.GRASS_BLOCK) {
                if (!treePlaced[dx][dz] && rnd.nextDouble() < 0.05) {

                    int minX = Math.max(0, dx - 2), maxX = Math.min(15, dx + 2);
                    int minZ = Math.max(0, dz - 2), maxZ = Math.min(15, dz + 2);
                    for (int nx = minX; nx <= maxX; nx++) {
                        for (int nz = minZ; nz <= maxZ; nz++) {
                            if (treePlaced[nx][nz]) return;
                        }
                    }
                    tryPlaceSmallTree(chunk, dx, dz, groundY, rnd);
                    treePlaced[dx][dz] = true;
                    return;
                }
                if (rnd.nextDouble() < 0.35) {
                    placeGrass(chunk, dx, dz, groundY, rnd);
                }
            }
            return;
        }

        if ("forest".equals(tag) || "village_green".equals(tag) || "pitch".equals(tag)
                || "meadow".equals(tag) || "grass".equals(tag) || "grassland".equals(tag)
                || "heath".equals(tag) || "scrub".equals(tag)) {

            if (surfaceMaterial != Material.GRASS_BLOCK) return;

            double treeChance;
            double grassChance;
            if ("forest".equals(tag)) {
                treeChance = 0.06;
                grassChance = 0.30;
            } else if ("meadow".equals(tag)) {
                treeChance = 0.005;
                grassChance = 0.60;
            } else if ("grass".equals(tag) || "grassland".equals(tag)) {
                treeChance = 0.02;
                grassChance = 0.55;
            } else {
                treeChance = 0.0;
                grassChance = 0.30;
            }

            if (!treePlaced[dx][dz] && rnd.nextDouble() < treeChance) {
                int minX = Math.max(0, dx - 2), maxX = Math.min(15, dx + 2);
                int minZ = Math.max(0, dz - 2), maxZ = Math.min(15, dz + 2);
                for (int nx = minX; nx <= maxX; nx++) {
                    for (int nz = minZ; nz <= maxZ; nz++) {
                        if (treePlaced[nx][nz]) return;
                    }
                }
                tryPlaceSmallTree(chunk, dx, dz, groundY, rnd);
                treePlaced[dx][dz] = true;
                return;
            }

            if (rnd.nextDouble() < grassChance) {
                placeGrass(chunk, dx, dz, groundY, rnd);
            }
            return;
        }
    }

    private void tryPlaceSmallTree(ChunkData chunk, int dx, int dz, int groundY, Random rng) {
        if (groundY >= 250) return;
        int trunkHeight = 3 + rng.nextInt(3);
        int leafCenterY = groundY + trunkHeight;
        if (leafCenterY + 2 > 255) return;

        for (int y = 1; y <= trunkHeight; y++) {
            chunk.setBlock(dx, groundY + y, dz, Material.OAK_LOG);
        }

        int leafRadius = 2;
        for (int lx = -leafRadius; lx <= leafRadius; lx++) {
            for (int lz = -leafRadius; lz <= leafRadius; lz++) {
                for (int ly = -1; ly <= 1; ly++) {
                    double dist = Math.sqrt(lx * lx + lz * lz + (ly * 1.2) * (ly * 1.2));
                    if (dist <= (leafRadius + 0.2)) {
                        int bx = dx + lx;
                        int by = leafCenterY + ly;
                        int bz = dz + lz;
                        if (bx >= 0 && bx < 16 && bz >= 0 && bz < 16 && by > MC_MIN_Y && by < WORLD_MAX_Y) {
                            chunk.setBlock(bx, by, bz, Material.OAK_LEAVES);
                        }
                    }
                }
            }
        }
    }

    private void placeGrass(ChunkData chunk, int dx, int dz, int groundY, Random rng) {
        int y = Math.min(WORLD_MAX_Y, groundY + 1);
        chunk.setBlock(dx, y, dz, Material.TALL_GRASS);
    }

    public boolean isInsideAnyBuilding(int wx, int wz) {
        if (mapData == null || mapData.buildings == null) return false;
        for (BuildingPolygon bp : mapData.buildings) {
            if (bp != null && bp.contains(wx, wz)) return true;
        }
        return false;
    }

    public double getBuildingHeightAt(int wx, int wz) {
        if (mapData == null || mapData.buildings == null) return METERS_PER_BLOCK * 2;
        for (BuildingPolygon bp : mapData.buildings) {
            if (bp != null && bp.contains(wx, wz)) return bp.heightMeters;
        }
        return METERS_PER_BLOCK * 2;
    }

    public void applySurface(ChunkData chunk, int dx, int dz, int height, Material mat) {
        if (mat == Material.WATER) {
            for (int y = MC_MIN_Y; y <= height; y++) chunk.setBlock(dx, y, dz, Material.WATER);
        } else if (mat == Material.DEEPSLATE_BRICKS || mat == Material.STONE_BRICKS
                || mat == Material.GRAY_CONCRETE) {
            chunk.setBlock(dx, height, dz, mat);
        } else {
            chunk.setBlock(dx, height, dz, mat != null ? mat : Material.GRASS_BLOCK);
        }
    }

    public void placeRailAt(ChunkData chunk, int dx, int dz, int groundY, Random rng) {
        int railY = groundY + 1;
        if (railY <= MC_MIN_Y || railY >= WORLD_MAX_Y) return;

        chunk.setBlock(dx, railY - 1, dz, Material.GRAVEL);

        if ((dx + dz) % 3 == 0) {
            chunk.setBlock(dx, railY - 1, dz, Material.OAK_PLANKS);
        }

        chunk.setBlock(dx, railY, dz, Material.RAIL);
    }

    private int computeRoof(BuildingPolygon building, int wx, int wz, int blocksHigh) {
        if (building == null) return 0;
        String roof = building.roofShape;
        if (roof == null) return 0;
        roof = roof.trim().toLowerCase();
        if (roof.isEmpty() || roof.contains("flat")) return 0;

        boolean isSkillion = roof.contains("skillion");

        if (building.xs == null || building.zs == null || building.xs.length == 0) return 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < building.xs.length && i < building.zs.length; i++) {
            minX = Math.min(minX, building.xs[i]);
            maxX = Math.max(maxX, building.xs[i]);
            minZ = Math.min(minZ, building.zs[i]);
            maxZ = Math.max(maxZ, building.zs[i]);
        }

        int widthX = Math.max(0, maxX - minX + 1);
        int depthZ = Math.max(0, maxZ - minZ + 1);
        if (widthX == 0 || depthZ == 0) return 0;

        boolean longerSide = widthX >= depthZ;
        if (isSkillion) {
            double normalizedPos;
            if (longerSide) {
                normalizedPos = (wx - minX) / (double) widthX;
            } else {
                normalizedPos = (wz - minZ) / (double) depthZ;
            }
            normalizedPos = clamp(normalizedPos, 0.0, 1.0);
            return (int) Math.round(normalizedPos * blocksHigh);
        } else {
            double normalizedDistanceFromCenter;
            if (longerSide) {
                double pos = (wx - minX) / (double) widthX;
                normalizedDistanceFromCenter = Math.abs(pos - 0.5) * 2.0;
            } else {
                double pos = (wz - minZ) / (double) depthZ;
                normalizedDistanceFromCenter = Math.abs(pos - 0.5) * 2.0;
            }
            double ratio = 1.0 - normalizedDistanceFromCenter;
            ratio = clamp(ratio, 0.0, 1.0);
            return (int) Math.round(ratio * blocksHigh);
        }
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}