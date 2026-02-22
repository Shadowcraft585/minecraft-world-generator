package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ZombieMinigame {

    public JavaPlugin plugin;
    public Player player;
    public Random rnd = new Random();

    public int initialCount = 2;
    public int incrementPerWave = 1;
    public long waveIntervalTicks = 1000L;
    public double enchantmentChance = 0.02;

    public double minSpawnRadius = 6.0;
    public double maxSpawnRadius = 18.0;

    public List<EntityType> variants = Arrays.asList(
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.HUSK,
            EntityType.DROWNED
    );

    public int wave = 0;
    private BukkitRunnable task;

    public ZombieMinigame(JavaPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void start() {
        if (task != null) return;

        try {
            String startingZombies = XMLReader.getVariable("ZombieMinigame", "startingZombies");
            if (startingZombies != null && !startingZombies.isBlank())
                initialCount = Integer.parseInt(startingZombies);
        } catch (Exception ignored) { }

        try {
            String increasePerWave = XMLReader.getVariable("ZombieMinigame", "increasePerWave");
            if (increasePerWave != null && !increasePerWave.isBlank())
                incrementPerWave = Integer.parseInt(increasePerWave);
        } catch (Exception ignored) { }

        try {
            String newWaveTimer = XMLReader.getVariable("ZombieMinigame", "newWaveTimer");
            if (newWaveTimer != null && !newWaveTimer.isBlank())
                waveIntervalTicks = Long.parseLong(newWaveTimer);
        } catch (Exception ignored) { }

        try {
            String minSpawnBlockDistance = XMLReader.getVariable("ZombieMinigame", "minSpawnBlockDistance");
            if (minSpawnBlockDistance != null && !minSpawnBlockDistance.isBlank())
                minSpawnRadius = Double.parseDouble(minSpawnBlockDistance);
        } catch (Exception ignored) { }

        try {
            String maxSpawnBlockDistance = XMLReader.getVariable("ZombieMinigame", "maxSpawnBlockDistance");
            if (maxSpawnBlockDistance != null && !maxSpawnBlockDistance.isBlank())
                maxSpawnRadius = Double.parseDouble(maxSpawnBlockDistance);
        } catch (Exception ignored) { }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stop();
                    return;
                }
                spawnWave();
            }
        };
        task.runTaskTimer(plugin, 0L, waveIntervalTicks);
        plugin.getLogger().info("ZombieMinigame started for " + player.getName());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        plugin.getLogger().info("ZombieMinigame stopped for " + player.getName());
    }

    private void spawnWave() {
        wave++;

        enchantmentChance += 0.02;

        World world = player.getWorld();
        if (world == null) return;

        int count = initialCount + (wave - 1) * incrementPerWave;

        double armorChance = Math.min(0.6, 0.01 + wave * 0.005);
        double weaponChance = Math.min(0.65, 0.01 + wave * 0.004);

        for (int i = 0; i < count; i++) {
            Location spawn = findSafeSpawnNear(player.getLocation(), world);
            if (spawn == null) continue;

            EntityType type = variants.get(rnd.nextInt(variants.size()));
            Entity ent = world.spawnEntity(spawn, type);

            if (!(ent instanceof LivingEntity)) continue;
            LivingEntity entity = (LivingEntity) ent;

            if (entity instanceof Creature) {
                ((Creature) entity).setTarget(player);
            }

            if (rnd.nextDouble() < armorChance && entity.getEquipment() != null) {
                ItemStack helmet, chest, legs, boots;
                if (wave < 8) {
                    helmet = new ItemStack(Material.LEATHER_HELMET);
                    chest = new ItemStack(Material.LEATHER_CHESTPLATE);
                    legs = new ItemStack(Material.LEATHER_LEGGINGS);
                    boots = new ItemStack(Material.LEATHER_BOOTS);
                } else if (wave < 20) {
                    helmet = new ItemStack(Material.IRON_HELMET);
                    chest = new ItemStack(Material.IRON_CHESTPLATE);
                    legs = new ItemStack(Material.IRON_LEGGINGS);
                    boots = new ItemStack(Material.IRON_BOOTS);
                } else {
                    helmet = new ItemStack(Material.DIAMOND_HELMET);
                    chest = new ItemStack(Material.DIAMOND_CHESTPLATE);
                    legs = new ItemStack(Material.DIAMOND_LEGGINGS);
                    boots = new ItemStack(Material.DIAMOND_BOOTS);
                }

                if (rnd.nextDouble() < 0.6) {
                    maybeEnchant(helmet);
                    entity.getEquipment().setHelmet(helmet);
                }
                if (rnd.nextDouble() < 0.5) {
                    maybeEnchant(chest);
                    entity.getEquipment().setChestplate(chest);
                }
                if (rnd.nextDouble() < 0.4) {
                    maybeEnchant(legs);
                    entity.getEquipment().setLeggings(legs);
                }
                if (rnd.nextDouble() < 0.5) {
                    maybeEnchant(boots);
                    entity.getEquipment().setBoots(boots);
                }
            }

            if (rnd.nextDouble() < weaponChance && entity.getEquipment() != null) {
                ItemStack weapon;
                if (wave < 8) {
                    weapon = new ItemStack(Material.WOODEN_SWORD);
                } else if (wave < 20) {
                    weapon = new ItemStack(Material.IRON_SWORD);
                } else {
                    weapon = new ItemStack(Material.DIAMOND_SWORD);
                }
                entity.getEquipment().setItemInMainHand(weapon);
            }
        }
    }

    private Location findSafeSpawnNear(Location center, World world) {
        int playerX = center.getBlockX();
        int playerZ = center.getBlockZ();

        int minRadiusInt = Math.max(0, (int) Math.round(minSpawnRadius));
        int maxRadiusInt = Math.max(1, (int) Math.ceil(maxSpawnRadius));
        int minRadiusInt2 = minRadiusInt * minRadiusInt;
        int maxRadiusInt2 = maxRadiusInt * maxRadiusInt;

        java.util.List<Location> candidates = new java.util.ArrayList<>();

        for (int distanceX = -maxRadiusInt; distanceX <= maxRadiusInt; distanceX++) {
            for (int distanceZ = -maxRadiusInt; distanceZ <= maxRadiusInt; distanceZ++) {
                int dist2 = distanceX * distanceX + distanceZ * distanceZ;
                if (dist2 < minRadiusInt2 || dist2 > maxRadiusInt2) continue;

                int x = playerX + distanceX;
                int z = playerZ + distanceZ;

                int topY = world.getHighestBlockYAt(x, z);
                if (topY <= -63) continue;

                if (world.getBlockAt(x, topY, z).getType().isSolid()
                        && !world.getBlockAt(x, topY + 1, z).getType().isSolid()) {
                    candidates.add(new Location(world, x + 0.5, topY + 1.0, z + 0.5));
                }
            }
        }

        if (candidates.isEmpty()) return null;

        return candidates.get(rnd.nextInt(candidates.size()));
    }

    private void maybeEnchant(ItemStack item) {
        if (item == null) return;
        if (rnd.nextDouble() >= enchantmentChance) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Enchantment> possible = new ArrayList<>();
        Material type = item.getType();
        String name = type.name();

        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            possible.add(Enchantment.PROTECTION);
            possible.add(Enchantment.THORNS);
        } else if (name.endsWith("_SWORD")) {
            possible.add(Enchantment.SHARPNESS);
            possible.add(Enchantment.KNOCKBACK);
            possible.add(Enchantment.FIRE_ASPECT);
        }

        if (possible.isEmpty()) return;

        java.util.Collections.shuffle(possible, rnd);
        int enchCount = 1 + rnd.nextInt(Math.min(2, possible.size()));
        for (int i = 0; i < enchCount; i++) {
            Enchantment e = possible.get(i);
            int level = (int) (Math.random() * e.getMaxLevel()) + 1;
            meta.addEnchant(e, level, true);
        }
        item.setItemMeta(meta);
    }
}