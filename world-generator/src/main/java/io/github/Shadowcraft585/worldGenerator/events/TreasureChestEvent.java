package io.github.Shadowcraft585.worldGenerator.events;

import io.github.Shadowcraft585.worldGenerator.PlayerPropertyStore;
import io.github.Shadowcraft585.worldGenerator.PlayerMoney;
import io.github.Shadowcraft585.worldGenerator.BuildingPolygon;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

public class TreasureChestEvent implements RandomEvent {
    @Override
    public void run(RandomEventContext context) {
        Optional<Player> op = context.randomPlayer();
        if (op.isEmpty()) return;
        Player p = op.get();

        PlayerMoney pm = context.plugin.getPlayerMoney();
        PlayerPropertyStore store = context.plugin.propertyStore;
        if (pm == null) return;

        double amount = 50 + context.rng.nextInt(451);

        String foundAt = null;
        if (store != null) {
            Set<String> owned = store.getOwnedIds(p);
            if (!owned.isEmpty() && context.plugin.mapData != null && context.plugin.mapData.buildings != null) {
                List<BuildingPolygon> ownedBp = new ArrayList<>();
                for (BuildingPolygon b : context.plugin.mapData.buildings) {
                    if (owned.contains(PlayerPropertyStore.idFor(b))) ownedBp.add(b);
                }
                if (!ownedBp.isEmpty()) {
                    BuildingPolygon sel = ownedBp.get(context.rng.nextInt(ownedBp.size()));
                    foundAt = sel.getStreetName();
                }
            }
        }

        pm.deposit(p, amount);

        Bukkit.getOnlinePlayers().forEach(f -> f.sendMessage(ChatColor.GOLD +
                "A dusty trunk uncovered beneath a property sends excitement through town. Who struck it rich?"));

        if (foundAt != null) {
            p.sendMessage(ChatColor.GOLD + "While clearing out the basement of one of your buildings you uncovered a chest and found" + Math.round(amount) + "! Lucky find.");
        } else {
            p.sendMessage(ChatColor.GOLD + "You found a hidden chest and gained $" + Math.round(amount) + "!");
        }
    }
}