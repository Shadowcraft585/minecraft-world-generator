package io.github.Shadowcraft585.worldGenerator.cards;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import io.github.Shadowcraft585.worldGenerator.PlayerMoney;

public class DrainMoneyCard implements Card {

    @Override public String getKey() {
        return "drain_money";
    }

    @Override public String getDisplayName() {
        return ChatColor.RED + "Bank Run";
    }

    @Override public String getDescription() {
        return "All other players lose 5% of their balance.";
    }

    @Override
    public void apply(CardContext context) {
        PlayerMoney pm = context.plugin.getPlayerMoney();
        if (pm == null) return;
        int affected = 0;
        for (Player p : context.world.getPlayers()) {
            if (p.getUniqueId().equals(context.player.getUniqueId())) continue;
            double bal = pm.getBalance(p);
            double take = bal * 0.05;
            if (take > 0) {
                pm.withdraw(p, take);
                p.sendMessage(ChatColor.RED + "You lost " + Math.round(take) + " due to a bank event.");
                affected++;
            }
        }
        context.player.sendMessage(ChatColor.GREEN + "All other players lost 5% of their balance (" + affected + " players affected).");
    }
}