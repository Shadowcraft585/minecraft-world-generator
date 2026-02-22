package io.github.Shadowcraft585.worldGenerator.cards;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class CardItemUtil {
    public static final String KEY = "card-key";

    public static ItemStack makeCardPaper(JavaPlugin plugin, Card card) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(card.getDisplayName());
            meta.setLore(List.of(card.getDescription(), "Right-click to use."));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, KEY), PersistentDataType.STRING, card.getKey());
            paper.setItemMeta(meta);
        }
        return paper;
    }

    public static String readCardKey(JavaPlugin plugin, ItemStack item) {
        if (item == null) return null;
        if (item.getType() != Material.PAPER) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(new NamespacedKey(plugin, KEY), PersistentDataType.STRING);
    }
}