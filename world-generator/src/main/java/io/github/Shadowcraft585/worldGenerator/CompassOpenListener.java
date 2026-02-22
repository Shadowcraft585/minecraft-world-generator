package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;

public class CompassOpenListener implements Listener {
    private final JavaPlugin plugin;
    private final OwnedBuildingsUI buildingsUI;
    private final NamespacedKey compassKey;

    public CompassOpenListener(JavaPlugin plugin, OwnedBuildingsUI buildingsUI) {
        this.plugin = plugin;
        this.buildingsUI = buildingsUI;
        this.compassKey = new NamespacedKey(plugin, "open_buildings_compass");
    }

    public ItemStack makeOpenCompass() {
        ItemStack comp = new ItemStack(Material.COMPASS);
        ItemMeta meta = comp.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Building Menu");
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, "open_buildings");
            comp.setItemMeta(meta);
        }
        return comp;
    }

    private boolean isProtectedCompass(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.COMPASS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(compassKey, PersistentDataType.STRING);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent ev) {
        Action action = ev.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        if (ev.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = ev.getItem();
        if (!isProtectedCompass(item)) return;

        ev.setCancelled(true);

        Player p = ev.getPlayer();
        if (buildingsUI != null) {
            buildingsUI.openBuildings(p);
        }
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent ev) {
        ItemStack dropped = ev.getItemDrop().getItemStack();
        if (isProtectedCompass(dropped)) {
            ev.setCancelled(true);
            ev.getPlayer().sendMessage("You cannot drop the compass.");
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent ev) {
        ItemStack main = ev.getMainHandItem();
        ItemStack off = ev.getOffHandItem();
        if (isProtectedCompass(main) || isProtectedCompass(off)) {
            ev.setCancelled(true);
            ev.getPlayer().sendMessage("You cannot swap the compass to the other hand.");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent ev) {
        ev.getDrops().removeIf(this::isProtectedCompass);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent ev) {
        ItemStack current = ev.getCurrentItem();
        if (isProtectedCompass(current)) {
            InventoryAction action = ev.getAction();
            if (action == InventoryAction.DROP_ONE_SLOT || action == InventoryAction.DROP_ALL_SLOT
                    || action == InventoryAction.DROP_ONE_CURSOR || action == InventoryAction.DROP_ALL_CURSOR
                    || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.COLLECT_TO_CURSOR) {
                ev.setCancelled(true);
                if (ev.getWhoClicked() instanceof Player) {
                    ((Player) ev.getWhoClicked()).sendMessage("You cannot move or drop the compass.");
                }
            }
        }
    }
}