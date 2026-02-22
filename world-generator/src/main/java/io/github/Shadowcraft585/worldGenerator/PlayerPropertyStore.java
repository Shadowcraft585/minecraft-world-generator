package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PlayerPropertyStore {

    private final NamespacedKey key;

    public PlayerPropertyStore(JavaPlugin plugin, String keyName) {
        this.key = new NamespacedKey(plugin, keyName);
    }

    public static String idFor(BuildingPolygon bp) {
        if (bp == null) return "";

        int hx = 0;
        int hz = 0;

        if (bp.xs != null) {
            hx = Arrays.hashCode(bp.xs);
        }
        if (bp.zs != null) {
            hz = Arrays.hashCode(bp.zs);
        }
        return Integer.toHexString(hx) + "_" + Integer.toHexString(hz);
    }

    public Set<String> getOwnedIds(Player p) {
        if (p == null || !p.isOnline()) return Collections.emptySet();
        String raw = p.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return Collections.emptySet();
        String[] parts = raw.split(";");
        Set<String> out = Arrays.stream(parts)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
        return out;
    }

    private void writeOwnedIds(Player p, Set<String> ids) {
        if (p == null || !p.isOnline()) return;
        String raw = String.join(";", ids);
        p.getPersistentDataContainer().set(key, PersistentDataType.STRING, raw);
    }

    public boolean owns(Player p, String buildingId) {
        if (p == null || buildingId == null) return false;
        return getOwnedIds(p).contains(buildingId);
    }

    public void addOwnership(Player p, String buildingId) {
        if (p == null || buildingId == null) return;
        Set<String> ids = new HashSet<>(getOwnedIds(p));
        if (ids.add(buildingId)) writeOwnedIds(p, ids);
    }

    public void removeOwnership(Player p, String buildingId) {
        if (p == null || buildingId == null) return;
        Set<String> ids = new HashSet<>(getOwnedIds(p));
        if (ids.remove(buildingId)) writeOwnedIds(p, ids);
    }
}