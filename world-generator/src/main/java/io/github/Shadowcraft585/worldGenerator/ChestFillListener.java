package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChestFillListener implements Listener {
    public Plugin plugin;
    public Map<Long,Integer> chestMap;

    public Map<Long, Inventory> activeInventories = new ConcurrentHashMap<>();
    public Set<Long> looted = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static  int WEIGHT_FOOD = 70;
    public static int WEIGHT_SUPPLIES = 20;
    public static int WEIGHT_WEAPONS = 10;

    public long INVENTORY_TIMEOUT_MS = 5 * 60 * 1000L;
    public Map<Long, Long> inventoryTimestamp = new ConcurrentHashMap<>();

    public ChestFillListener(Plugin plugin, Map<Long,Integer> chestMap) {
        this.plugin = plugin;
        this.chestMap = chestMap != null ? chestMap : Collections.emptyMap();

        String foodChance = XMLReader.getVariable("ChestFillListener", "foodChance");
        WEIGHT_FOOD = Integer.parseInt(foodChance);

        String suppliesChance = XMLReader.getVariable("ChestFillListener", "suppliesChance");
        WEIGHT_SUPPLIES = Integer.parseInt(suppliesChance);

        String toolChance = XMLReader.getVariable("ChestFillListener", "toolChance");
        WEIGHT_WEAPONS = Integer.parseInt(toolChance);

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<Long, Long> e : new ArrayList<>(inventoryTimestamp.entrySet())) {
                long key = e.getKey();
                if (now - e.getValue() > INVENTORY_TIMEOUT_MS) {
                    activeInventories.remove(key);
                    inventoryTimestamp.remove(key);
                }
            }
        }, 20L, 20L * 60L);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent ev) {
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent ev) {
        if (ev.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = ev.getClickedBlock();
        if (b == null) return;
        Material t = b.getType();
        if (t != Material.CHEST && t != Material.TRAPPED_CHEST && t != Material.BARREL) return;

        long key = Raster.keyFor(b.getX(), b.getZ());
        if (!chestMap.containsKey(key)) return;

        ev.setCancelled(true);
        Player player = (Player) ev.getPlayer();

        if (looted.contains(key)) {
            player.sendMessage("This container has already been looted.");
            player.openInventory(Bukkit.createInventory(null, 27, "Chest"));
            return;
        }

        Inventory inv = activeInventories.get(key);
        if (inv == null) {
            inv = Bukkit.createInventory(null, 27, "Chest");
            fillInventoryWithLoot(inv, new Random());
            activeInventories.put(key, inv);
        }
        inventoryTimestamp.put(key, System.currentTimeMillis());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent ev) {
        if (ev.getWhoClicked() == null) return;
        Inventory clicked = ev.getClickedInventory();
        if (clicked == null) return;
        Long foundKey = null;
        for (Map.Entry<Long, Inventory> e : activeInventories.entrySet()) {
            if (e.getValue() == clicked) { foundKey = e.getKey(); break; }
        }
        if (foundKey == null) return;

        boolean likelyRemoval = true;
        if (likelyRemoval) {
            looted.add(foundKey);
            activeInventories.remove(foundKey);
            inventoryTimestamp.remove(foundKey);
        }
    }

    public void fillInventoryWithLoot(Inventory inv, Random rng) {
        int itemCount = 1 + rng.nextInt(3);
        for (int i = 0; i < itemCount; i++) {
            int tierRoll = rng.nextInt(WEIGHT_FOOD + WEIGHT_SUPPLIES + WEIGHT_WEAPONS);
            List<LootTables.LootEntry> table;
            if (tierRoll < WEIGHT_FOOD) table = LootTables.FOOD;
            else if (tierRoll < WEIGHT_FOOD + WEIGHT_SUPPLIES) table = LootTables.SUPPLIES;
            else table = LootTables.WEAPONS;

            LootTables.LootEntry le = LootTables.pickWeighted(table, rng);
            if (le == null) continue;
            int amt = le.minAmount + rng.nextInt(le.maxAmount - le.minAmount + 1);
            ItemStack out = le.item.clone();
            out.setAmount(Math.min(out.getMaxStackSize(), amt));
            int slot = rng.nextInt(inv.getSize());
            inv.setItem(slot, out);
        }
    }

    public void resetAllChests() {
        looted.clear();
        activeInventories.clear();
        inventoryTimestamp.clear();
    }
}