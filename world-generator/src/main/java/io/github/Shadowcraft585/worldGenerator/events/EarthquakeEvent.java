package io.github.Shadowcraft585.worldGenerator.events;

import io.github.Shadowcraft585.worldGenerator.BuildingPolygon;
import io.github.Shadowcraft585.worldGenerator.PlayerPropertyStore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;


import java.util.List;
import java.util.stream.Collectors;

public class EarthquakeEvent implements RandomEvent {

    @Override
    public void run(RandomEventContext context) {
        if (context.plugin.mapData == null || context.plugin.mapData.buildings == null) return;
        List<BuildingPolygon> buildings = context.plugin.mapData.buildings;

        List<BuildingPolygon> destroyed = buildings.stream()
                .filter(b -> b.explicitLevels >= 4)
                .collect(Collectors.toList());

        if (destroyed.isEmpty()) {
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(ChatColor.YELLOW + "Earthquake occurred but no tall buildings were found."));
            return;
        }

        PlayerPropertyStore store = context.plugin.propertyStore;
        for (BuildingPolygon b : destroyed) {
            b.value = 0.0;
            b.earning = 0.0;
        }

        if (store != null) {
            for (Player player : context.players) {
                for (BuildingPolygon b : destroyed) {
                    String id = PlayerPropertyStore.idFor(b);
                    if (store.owns(player, id)) {
                        store.removeOwnership(player, id);
                        player.sendMessage(ChatColor.RED + "Your high building was destroyed and removed from your properties.");
                    }
                }
            }
        }

        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(ChatColor.RED + "A massive Earthquake destroyed " + destroyed.size() +
                " tall buildings. Many lives were lost"));
    }
}