package io.github.Shadowcraft585.worldGenerator.cards;

import io.github.Shadowcraft585.worldGenerator.BuildingPolygon;
import io.github.Shadowcraft585.worldGenerator.PlayerPropertyStore;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class StealHouseCard implements Card {

    @Override public String getKey() {
        return "steal_house";
    }

    @Override public String getDisplayName() {
        return ChatColor.DARK_PURPLE + "Claim a House";
    }

    @Override public String getDescription() {
        return "Steal one random house from another player";
    }

    @Override
    public void apply(CardContext context) {
        PlayerPropertyStore store = context.plugin.propertyStore;
        if (store == null) { context.player.sendMessage(ChatColor.RED + "Property system not available."); return; }

        List<BuildingPolygon> candidates = context.plugin.mapData.buildings.stream()
                .filter(b -> {
                    String id = PlayerPropertyStore.idFor(b);
                    for (Player p : context.world.getPlayers()) {
                        if (p.getUniqueId().equals(context.player.getUniqueId())) continue;
                        if (store.owns(p, id)) return true;
                    }
                    return false;
                }).collect(Collectors.toList());

        if (candidates.isEmpty()) {
            context.player.sendMessage(ChatColor.YELLOW + "No other players' houses to claim.");
            return;
        }

        BuildingPolygon pick = candidates.get(context.rng.nextInt(candidates.size()));
        String id = PlayerPropertyStore.idFor(pick);
        for (Player p : context.world.getPlayers()) {
            if (store.owns(p, id)) {
                store.removeOwnership(p, id);
                store.addOwnership(context.player, id);
                context.player.sendMessage(ChatColor.GREEN + "You claimed a house previously owned by " + p.getName() + "!");
                p.sendMessage(ChatColor.RED + "One of your houses was claimed by " + context.player.getName() + "!");
                return;
            }
        }
        context.player.sendMessage(ChatColor.YELLOW + "Could not transfer ownership (owner offline or AI).");
    }
}