package io.github.Shadowcraft585.worldGenerator.events;

import io.github.Shadowcraft585.worldGenerator.BuildingPolygon;
import io.github.Shadowcraft585.worldGenerator.PlayerPropertyStore;
import io.github.Shadowcraft585.worldGenerator.PlayerMoney;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.Optional;

public class RenovationEvent implements RandomEvent {

    @Override
    public void run(RandomEventContext context) {
        Optional<Player> victim = context.randomPlayer();
        if (victim.isEmpty()) return;
        Player p = victim.get();

        PlayerMoney pm = context.plugin.getPlayerMoney();
        PlayerPropertyStore store = context.plugin.propertyStore;
        if (pm == null || store == null) {
            p.sendMessage(ChatColor.RED + "Internal services unavailable.");
            return;
        }

        double balance = pm.getBalance(p);
        double toTake = balance * 0.2;
        pm.withdraw(p, toTake);

        Set<String> owned = store.getOwnedIds(p);
        int changed = 0;
        if (context.plugin.mapData != null && context.plugin.mapData.buildings != null) {
            for (BuildingPolygon b : context.plugin.mapData.buildings) {
                if (b == null) continue;
                if (owned.contains(PlayerPropertyStore.idFor(b))) {
                    b.earning = b.earning * 1.05;
                    changed++;
                }
            }
        }

        p.sendMessage(ChatColor.GOLD + "You were charged" + Math.round(toTake) +
                "for required renovations. You can expect the renting prices to rise");
    }
}