package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

public class PlayerMoney {
    private final NamespacedKey key;

    public PlayerMoney(JavaPlugin plugin, String keyName) {
        this.key = new NamespacedKey(plugin, keyName);
    }

    public double getBalance(Player p) {
        if (p == null || !p.isOnline()) return 0.0;
        Double v = p.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        return (v == null) ? 0.0 : v;
    }

    public void setBalance(Player p, double amount) {
        if (p == null || !p.isOnline()) return;
        p.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, Math.max(0.0, amount));
    }

    public void deposit(Player p, double amount) {
        if (p == null || !p.isOnline() || amount <= 0) return;
        double cur = getBalance(p);
        setBalance(p, cur + amount);
    }

    public boolean withdraw(Player p, double amount) {
        if (p == null || !p.isOnline() || amount <= 0) return false;
        double cur = getBalance(p);
        if (cur < amount) return false;
        setBalance(p, cur - amount);
        return true;
    }

    public boolean hasBalance(org.bukkit.entity.Player p) {
        if (p == null || !p.isOnline()) return false;
        return p.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE);
    }
}