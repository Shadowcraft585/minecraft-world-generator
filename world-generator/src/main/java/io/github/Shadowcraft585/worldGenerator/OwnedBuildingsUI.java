package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OwnedBuildingsUI implements Listener {

    private JavaPlugin plugin;
    private PlayerPropertyStore propertyStore;
    private PlayerMoney money;
    private NamespacedKey actionKey;
    private NamespacedKey buildingKey;

    private static int GUI_SIZE = 54;
    private static int MAX_ITEMS = 45;

    public OwnedBuildingsUI(JavaPlugin plugin, PlayerPropertyStore propertyStore, PlayerMoney money) {
        this.plugin = plugin;
        this.propertyStore = propertyStore;
        this.money = money;
        this.actionKey = new NamespacedKey(plugin, "simple_building_action");
        this.buildingKey = new NamespacedKey(plugin, "simple_building_id");
    }

    public void openBuildings(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory inv = createListInventory(p);
            if (inv != null) p.openInventory(inv);
        });
    }

    private Inventory createListInventory(Player p) {
        if (!(plugin instanceof WorldGenerator)) return null;
        WorldGenerator wg = (WorldGenerator) plugin;
        if (wg.mapData == null || wg.mapData.buildings == null) {
            p.sendMessage(ChatColor.RED + "Map data not loaded yet.");
            return null;
        }

        Inventory inv = Bukkit.createInventory(new ListHolder(), GUI_SIZE,
                ChatColor.BLACK + "Your Buildings");

        List<BuildingPolygon> owned = new ArrayList<>();
        for (BuildingPolygon bp : wg.mapData.buildings) {
            if (bp == null) continue;
            String id = PlayerPropertyStore.idFor(bp);
            if (propertyStore != null && propertyStore.owns(p, id)) {
                owned.add(bp);
            }
        }

        int count = Math.min(MAX_ITEMS, owned.size());
        for (int i = 0; i < count; i++) {
            BuildingPolygon b = owned.get(i);
            ItemStack it = new ItemStack(b.buildingMaterial != null ? b.buildingMaterial : Material.CHEST);
            ItemMeta meta = it.getItemMeta();
            if (meta == null) continue;

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Price: " + Math.round(b.value));
            lore.add(ChatColor.GRAY + "Income: " + Math.round(b.earning) + " /per turn");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open");
            meta.getPersistentDataContainer().set(buildingKey, PersistentDataType.STRING, PlayerPropertyStore.idFor(b));
            it.setItemMeta(meta);

            inv.setItem(i, it);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName(ChatColor.RED + "Close");
        cm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
        close.setItemMeta(cm);
        inv.setItem(49, close);

        return inv;
    }

    private Inventory createDetailInventory(BuildingPolygon b, Player p) {
        Inventory inv = Bukkit.createInventory(new DetailHolder(b), 27,
                ChatColor.DARK_PURPLE + "Building Info");

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.setDisplayName(ChatColor.YELLOW + "Info");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Price: " + Math.round(b.value));
            lore.add(ChatColor.GRAY + "Income: " + Math.round(b.earning) + " /per turn");
            im.setLore(lore);
            info.setItemMeta(im);
        }
        inv.setItem(11, info);

        ItemStack sell = new ItemStack(Material.RED_WOOL);
        ItemMeta sm = sell.getItemMeta();
        if (sm != null) {
            sm.setDisplayName(ChatColor.RED + "Sell Building");
            sm.setLore(Collections.singletonList(ChatColor.GRAY + "Click to sell for " + Math.round(b.value)));
            sm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "sell");
            sm.getPersistentDataContainer().set(buildingKey, PersistentDataType.STRING, PlayerPropertyStore.idFor(b));
            sell.setItemMeta(sm);
        }
        inv.setItem(13, sell);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta xm = close.getItemMeta();
        if (xm != null) {
            xm.setDisplayName(ChatColor.RED + "Close");
            xm.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
            close.setItemMeta(xm);
        }
        inv.setItem(22, close);

        return inv;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent ev) {
        if (!(ev.getWhoClicked() instanceof Player)) return;
        Player p = (Player) ev.getWhoClicked();
        Inventory top = ev.getView().getTopInventory();
        if (top == null) return;

        if (top.getHolder() instanceof ListHolder) {
            ev.setCancelled(true);
            ItemStack cur = ev.getCurrentItem();
            if (cur == null || cur.getType() == Material.AIR) return;
            ItemMeta meta = cur.getItemMeta();
            if (meta == null) return;
            String act = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (act == null) return;

            if ("open".equals(act)) {
                String id = meta.getPersistentDataContainer().get(buildingKey, PersistentDataType.STRING);
                BuildingPolygon building = findBuildingById(id);
                if (building == null) {
                    p.sendMessage(ChatColor.RED + "Gebäude nicht gefunden.");
                    p.closeInventory();
                    return;
                }
                p.openInventory(createDetailInventory(building, p));
                return;
            }

            if ("close".equals(act)) {
                p.closeInventory();
            }
            return;
        }

        if (top.getHolder() instanceof DetailHolder) {
            ev.setCancelled(true);
            ItemStack cur = ev.getCurrentItem();
            if (cur == null || cur.getType() == Material.AIR) return;
            ItemMeta meta = cur.getItemMeta();
            if (meta == null) return;
            String act = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (act == null) return;

            if ("sell".equals(act)) {
                String id = meta.getPersistentDataContainer().get(buildingKey, PersistentDataType.STRING);
                if (propertyStore == null || !propertyStore.owns(p, id)) {
                    p.sendMessage(ChatColor.RED + "Du besitzt dieses Gebäude nicht.");
                    p.closeInventory();
                    return;
                }
                BuildingPolygon b = findBuildingById(id);
                if (b == null) {
                    p.sendMessage(ChatColor.RED + "Gebäude nicht gefunden.");
                    p.closeInventory();
                    return;
                }
                double sellPrice = b.value;
                money.deposit(p, sellPrice);
                propertyStore.removeOwnership(p, id);
                p.sendMessage(ChatColor.GREEN + "Gebäude verkauft für " + Math.round(sellPrice));
                openBuildings(p);
                return;
            }

            if ("close".equals(act)) {
                p.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent ev) {
        Inventory top = ev.getView().getTopInventory();
        if (top != null && (top.getHolder() instanceof ListHolder || top.getHolder() instanceof DetailHolder)) {
            ev.setCancelled(true);
        }
    }

    private BuildingPolygon findBuildingById(String id) {
        if (!(plugin instanceof WorldGenerator)) return null;
        WorldGenerator wg = (WorldGenerator) plugin;
        if (wg.mapData == null || wg.mapData.buildings == null) return null;
        for (BuildingPolygon bp : wg.mapData.buildings) {
            if (bp == null) continue;
            if (PlayerPropertyStore.idFor(bp).equals(id)) return bp;
        }
        return null;
    }

    private static class ListHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    private static class DetailHolder implements InventoryHolder {
        final BuildingPolygon poly;
        DetailHolder(BuildingPolygon p) { this.poly = p; }
        @Override public Inventory getInventory() { return null; }
    }
}