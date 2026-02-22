package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

public class BuyWoolListener implements Listener {

    public static String KEY_NAME = "buy_wool";
    public JavaPlugin plugin;
    public NamespacedKey key;

    public BuyWoolListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public ItemStack makeWool() {
        ItemStack wool = new ItemStack(Material.GREEN_WOOL, 1);
        ItemMeta meta = wool.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Buy Tool");
            meta.setLore(List.of("Right-click to buy the building you're standing in."));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "1");
            wool.setItemMeta(meta);
        }
        return wool;
    }

    public boolean isWool(ItemStack item) {
        if (item == null) return false;

        if (item.getType() != Material.GREEN_WOOL) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        String tag = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return tag != null;
    }


    public boolean playerHasWool(Player p) {
        if (p == null || !p.isOnline()) return false;

        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) {
            if (isWool(it)) return true;
        }

        if (isWool(inv.getItemInOffHand())) return true;

        return false;
    }

    public void giveWool(Player p) {
        if (p == null || !p.isOnline()) return;
        if (playerHasWool(p)) return;
        p.getInventory().addItem(makeWool());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent ev) {
        Action a = ev.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = ev.getItem();
        if (item == null) return;

        if (item.getType() != Material.GREEN_WOOL) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String tag = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (tag == null) return;

        ev.setCancelled(true);

        Player p = ev.getPlayer();

        boolean ok = p.performCommand("buy");
        if (!ok) {
            p.sendMessage("Error: /buy could not be executed.");
        }
    }

    public void registerAndGive() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) {
            giveWool(p);
        }
    }
}