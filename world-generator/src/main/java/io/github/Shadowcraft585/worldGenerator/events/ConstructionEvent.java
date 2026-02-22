package io.github.Shadowcraft585.worldGenerator.events;

import io.github.Shadowcraft585.worldGenerator.BuildingPolygon;
import io.github.Shadowcraft585.worldGenerator.PlayerPropertyStore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;

public class ConstructionEvent implements RandomEvent {

    @Override
    public void run(RandomEventContext context) {
        if (context.plugin.mapData == null || context.plugin.mapData.buildings == null) {
            return;
        }
        List<BuildingPolygon> buildings = context.plugin.mapData.buildings;
        if (buildings.isEmpty()) return;

        int maxAttempts = 20;
        BuildingPolygon pick = null;
        String street = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            BuildingPolygon candidate = buildings.get(context.rng.nextInt(buildings.size()));
            String s = candidate.getStreetName();
            if (s != null) s = s.trim();
            if (s != null && !s.isEmpty() && !s.equalsIgnoreCase("empty")) {
                pick = candidate;
                street = s;
                break;
            }
        }

        final String streetNorm = street.trim();

        List<BuildingPolygon> affected = buildings.stream()
                .filter(b -> {
                    String s = b.getStreetName();
                    if (s == null) return false;
                    s = s.trim();
                    if (s.isEmpty() || s.equalsIgnoreCase("empty")) return false;
                    return streetNorm.equalsIgnoreCase(s);
                })
                .collect(Collectors.toList());

        if (affected.isEmpty()) {
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(
                    ChatColor.YELLOW + "Construction on '" + streetNorm + "' found no matching buildings."));
            return;
        }

        for (BuildingPolygon b : affected) {
            b.value = b.value * 1.5;
            b.earning = b.earning * 0.5;
        }

        String notify = ChatColor.YELLOW + "Work on" + streetNorm + "is underway, expect higher sale prices and temporarily lower rents nearby.";
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(notify));

        PlayerPropertyStore store = context.plugin.propertyStore;
        if (store != null) {
            for (Player player : context.players) {
                Set<String> owned = store.getOwnedIds(player);
                for (BuildingPolygon b : affected) {
                    if (owned.contains(PlayerPropertyStore.idFor(b))) {
                        player.sendMessage(ChatColor.RED + "Your building on " + streetNorm + " is affected by construction.");
                    }
                }
            }
        }
    }
}