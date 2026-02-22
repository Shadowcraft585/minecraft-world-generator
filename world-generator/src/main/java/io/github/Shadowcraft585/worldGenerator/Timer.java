package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

public class Timer {

    private final Map<UUID, Long> timer = new ConcurrentHashMap<>();

    public void start(Player p) {
        if (p == null) return;
        timer.put(p.getUniqueId(), System.currentTimeMillis());
    }

    public void stop(Player p) {
        if (p == null) return;
        timer.remove(p.getUniqueId());
    }

    public boolean isRunning(Player p) {
        return p != null && timer.containsKey(p.getUniqueId());
    }

    public String getTime(Player p) {
        if (p == null) return "00:00:00";
        Long start = timer.get(p.getUniqueId());
        if (start == null) {
            p.sendMessage("Timer nicht gestartet");
            return "00:00:00";
        }

        long sec = (System.currentTimeMillis() - start) / 1000L;
        long hours = sec / 3600;
        long minutes = (sec % 3600) / 60;
        long seconds = sec % 60;
        return hours + ":" + minutes + ":" + seconds;
    }
}
