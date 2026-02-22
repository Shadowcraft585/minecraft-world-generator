package io.github.Shadowcraft585.worldGenerator.cards;

import io.github.Shadowcraft585.worldGenerator.BuildingPolygon;
import io.github.Shadowcraft585.worldGenerator.PlayerPropertyStore;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.ArrayList;
import java.util.Optional;

public class DoubleHouseCard implements Card {

    @Override public String getKey() {
        return "double_house";
    }

    @Override public String getDisplayName() {
        return ChatColor.GOLD + "Double House";
    }

    @Override public String getDescription() {
        return "One of your houses will have its price and earnings doubled.";
    }

    @Override
    public boolean canUse(CardContext context) {
        if (context == null || context.player == null) return false;
        if (context.plugin.mapData == null || context.plugin.mapData.buildings == null) return false;
        return context.plugin.propertyStore.getOwnedIds(context.player).size() > 0;
    }

    @Override
    public void apply(CardContext context) {
        Optional<Player> human = context.actorAsPlayer();
        if (human.isPresent()) {
            Player p = human.get();
            PlayerPropertyStore store = context.plugin.propertyStore;
            Set<String> owned = store.getOwnedIds(p);
            if (owned.isEmpty()) {
                p.sendMessage(ChatColor.RED + "You own no buildings.");
                return;
            }
            List<BuildingPolygon> my = context.plugin.mapData.buildings.stream()
                    .filter(b -> owned.contains(PlayerPropertyStore.idFor(b)))
                    .collect(Collectors.toList());
            if (my.isEmpty()) {
                p.sendMessage(ChatColor.RED + "No valid buildings found.");
                return;
            }
            BuildingPolygon pick = my.get(context.rng.nextInt(my.size()));
            pick.setValue(pick.getValue() * 2.0);
            pick.setEarning(pick.getEarning() * 2.0);
            p.sendMessage(ChatColor.GREEN + "A random one of your houses has its value and earnings doubled!");
            return;
        }

        if (context.actorId != null) {
            Set<String> owned = context.plugin.getTurnManager().getAiOwnership(context.actorId);
            if (owned == null || owned.isEmpty()) {
                context.plugin.getLogger().info("AI " + context.actorId + " has no owned houses to double.");
                return;
            }

            List<String> ownedList = new ArrayList<>(owned);
            String pickId = ownedList.get(context.rng.nextInt(ownedList.size()));

            BuildingPolygon bp = null;
            if (context.plugin.mapData != null && context.plugin.mapData.buildings != null) {
                for (BuildingPolygon b : context.plugin.mapData.buildings) {
                    if (b == null) continue;
                    if (PlayerPropertyStore.idFor(b).equals(pickId)) {
                        bp = b;
                        break;
                    }
                }
            }

            if (bp == null) {
                context.plugin.getLogger().warning("AI " + context.actorId + " owned building id not found: " + pickId);
                return;
            }

            bp.setValue(bp.getValue() * 2.0);
            bp.setEarning(bp.getEarning() * 2.0);

            context.plugin.getLogger().info("AI " + context.actorId + " doubled value/earning of its building " + pickId);
            return;
        }
    }
}