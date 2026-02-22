package io.github.Shadowcraft585.worldGenerator.events;

import io.github.Shadowcraft585.worldGenerator.BuildingPolygon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.List;

public class InflationEvent implements RandomEvent {

    @Override
    public void run(RandomEventContext context) {
        if (context.plugin.mapData == null || context.plugin.mapData.buildings == null) return;
        List<BuildingPolygon> buildings = context.plugin.mapData.buildings;
        if (buildings.isEmpty()) return;

        double inflation = 0.05 + context.rng.nextDouble() * 0.25;

        for (BuildingPolygon b : buildings) {
            b.value = b.value * (1.0 + inflation);
        }

        String msg = ChatColor.GOLD + "Inflation changed the market. Property prices have gone up citywide. Factor this into your plans.";
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
    }
}