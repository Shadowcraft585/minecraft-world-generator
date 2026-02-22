package io.github.Shadowcraft585.worldGenerator.cards;

import io.github.Shadowcraft585.worldGenerator.WorldGenerator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class CardUseListener implements Listener {
    private final JavaPlugin plugin;
    private final CardRegistry registry;

    public CardUseListener(JavaPlugin plugin, CardRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent ev) {
        if (ev.getAction() != Action.RIGHT_CLICK_AIR && ev.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = ev.getPlayer();
        ItemStack item = ev.getItem();
        if (item == null) return;

        String key = CardItemUtil.readCardKey(plugin, item);
        if (key == null) return;

        Card card = registry.get(key);
        if (card == null) return;

        World w = p.getWorld();
        List<Player> online = new ArrayList<>(w.getPlayers());
        UUID actorId = p.getUniqueId();

        CardContext context = new CardContext((WorldGenerator) plugin, w, p, actorId, online, new Random());

        if (!card.canUse(context)) {
            p.sendMessage("You cannot use this card right now.");
            return;
        }

        item.setAmount(item.getAmount() - 1);

        card.apply(context);
    }
}