package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LootTables {

    public static class LootEntry {
        public final ItemStack item;
        public final int weight;
        public final int minAmount;
        public final int maxAmount;

        public LootEntry(ItemStack item, int weight, int minAmount, int maxAmount) {
            this.item = item;
            this.weight = Math.max(0, weight);
            this.minAmount = Math.max(1, minAmount);
            this.maxAmount = Math.max(this.minAmount, maxAmount);
        }
    }

    public static final List<LootEntry> FOOD = new ArrayList<>();
    public static final List<LootEntry> SUPPLIES = new ArrayList<>();
    public static final List<LootEntry> WEAPONS = new ArrayList<>();

    static {
        FOOD.add(new LootEntry(new ItemStack(Material.APPLE), 40, 1, 2));
        FOOD.add(new LootEntry(new ItemStack(Material.BREAD), 30, 1, 2));
        FOOD.add(new LootEntry(new ItemStack(Material.COOKED_BEEF), 15, 1, 1));
        FOOD.add(new LootEntry(new ItemStack(Material.COOKED_PORKCHOP), 10, 1, 1));
        FOOD.add(new LootEntry(new ItemStack(Material.MUSHROOM_STEW), 5, 1, 1));

        SUPPLIES.add(new LootEntry(new ItemStack(Material.STRING), 30, 1, 4));
        SUPPLIES.add(new LootEntry(new ItemStack(Material.TORCH), 25, 1, 16));
        SUPPLIES.add(new LootEntry(new ItemStack(Material.IRON_INGOT), 20, 1, 3));
        SUPPLIES.add(new LootEntry(new ItemStack(Material.GOLD_INGOT), 15, 1, 1));
        SUPPLIES.add(new LootEntry(new ItemStack(Material.FLINT), 10, 1, 4));

        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.LEATHER_HELMET)), 16, 1, 1));
        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.LEATHER_CHESTPLATE)), 12, 1, 1));
        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.LEATHER_LEGGINGS)), 14, 1, 1));
        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.LEATHER_BOOTS)), 18, 1, 1));
        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.IRON_SWORD)), 20, 1, 1));
        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.IRON_HELMET)), 12, 1, 1));
        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.IRON_CHESTPLATE)), 6, 1, 1));
        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.IRON_LEGGINGS)), 6, 1, 1));
        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.IRON_BOOTS)), 6, 1, 1));
        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.BOW)), 10, 1, 1));

        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.DIAMOND_SWORD)), 2, 1, 1));
        WEAPONS.add(new LootEntry(makeItem(new ItemStack(Material.DIAMOND_CHESTPLATE)), 1, 1, 1));
    }

    private static ItemStack makeItem(ItemStack stack) {
        return stack.clone();
    }

    public static LootEntry pickWeighted(List<LootEntry> table, Random rng) {
        if (table == null || table.isEmpty()) return null;
        int total = 0;
        for (LootEntry e : table) total += e.weight;
        if (total <= 0) return null;
        int r = rng.nextInt(total);
        int acc = 0;
        for (LootEntry e : table) {
            acc += e.weight;
            if (r < acc) return e;
        }
        return null;
    }
}