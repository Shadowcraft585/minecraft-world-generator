package io.github.Shadowcraft585.worldGenerator.events;

import io.github.Shadowcraft585.worldGenerator.BuildingPolygon;
import io.github.Shadowcraft585.worldGenerator.Raster;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.List;

public class PlagueEvent implements RandomEvent {

    @Override
    public void run(RandomEventContext context) {
        if (context.plugin.mapData == null || context.plugin.mapData.buildings == null) return;
        List<BuildingPolygon> buildings = context.plugin.mapData.buildings;
        if (buildings.isEmpty()) return;

        int affected = 0;
        for (BuildingPolygon b : buildings) {
            if (b.xs == null || b.zs == null || b.xs.length == 0) continue;
            int sumX = 0, sumZ = 0;
            for (int i = 0; i < b.xs.length; i++) { sumX += b.xs[i]; sumZ += b.zs[i]; }
            int cx = sumX / b.xs.length;
            int cz = sumZ / b.zs.length;
            long key = Raster.keyFor(cx, cz);

            String tag = null;
            if (context.plugin.mapData.landuseMap != null) tag = context.plugin.mapData.landuseMap.get(key);
            if (tag == null && context.plugin.mapData.amenityMap != null) tag = context.plugin.mapData.amenityMap.get(key);
            if (tag == null && context.plugin.mapData.naturalMap != null) tag = context.plugin.mapData.naturalMap.get(key);
            String t = (tag != null) ? tag.trim().toLowerCase() : "";

            if ("residential".equals(t) || "commercial".equals(t)) {
                b.earning = 0.0;
            } else {
                b.earning = b.earning * 0.5;
            }
            affected++;
        }

        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(ChatColor.DARK_GREEN +
                "Many people are dying, the rest are abandoning their houses, high density areas are especially affected"));
    }
}