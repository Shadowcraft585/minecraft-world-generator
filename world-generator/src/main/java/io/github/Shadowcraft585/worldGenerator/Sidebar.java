package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Sidebar {
    private final WorldGenerator plugin;
    private final Map<UUID, List<String>> lastLines = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBlockKey = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastSeenStreet = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastSeenArea = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastSeenValue = new ConcurrentHashMap<>();

    public Sidebar(WorldGenerator plugin) {
        this.plugin = plugin;
    }

    public void createFor(Player p) {
        if (p == null || !p.isOnline()) return;
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective obj = board.registerNewObjective("cashhud", "test", ChatColor.GOLD + "Information");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        p.setScoreboard(board);
        update(p);
    }

    public void removeFor(Player p) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        List<String> prev = lastLines.remove(id);
        lastBlockKey.remove(id);
        lastSeenStreet.remove(id);
        lastSeenArea.remove(id);
        lastSeenValue.remove(id);
        try {
            Scoreboard board = p.getScoreboard();
            if (board != null && prev != null) {
                for (String s : prev) {
                    try { board.resetScores(s); } catch (Throwable ignored) {}
                }
            }
            ScoreboardManager mgr = Bukkit.getScoreboardManager();
            if (mgr != null) p.setScoreboard(mgr.getNewScoreboard());
        } catch (Throwable ignored) {}
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    public void update(Player p) {
        if (p == null || !p.isOnline()) return;
        UUID id = p.getUniqueId();

        double balance = 0.0;
        try {
            PlayerMoney pm = plugin.getPlayerMoney();
            if (pm != null) balance = pm.getBalance(p);
        } catch (Throwable ignored) {}
        String cashLine = ChatColor.GREEN + "Cash: " + ChatColor.WHITE + Math.round(balance);

        int wx = p.getLocation().getBlockX();
        int wz = p.getLocation().getBlockZ();
        long key = Raster.keyFor(wx, wz);
        Long lastKey = lastBlockKey.get(id);
        if (!Objects.equals(lastKey, key)) {
            BuildingPolygon found = findBuildingAtBlockSimple(wx, wz);
            if (found != null) {
                String street = found.getStreetName();
                Double price = found.getValue();
                lastSeenStreet.put(id, street);
                lastSeenValue.put(id, price);
            }
            String area = readAreaFromMaps(key);
            if (area != null && !area.isBlank()) {
                lastSeenArea.put(id, area);
            }
            lastBlockKey.put(id, key);
        }

        String displayedStreet = lastSeenStreet.getOrDefault(id, "unknown");
        String displayedArea = lastSeenArea.getOrDefault(id, "unknown");
        String displayedEvent = plugin.getTurnManager().getActiveEventSummary();
        String displayedValue = "" + Math.round(lastSeenValue.getOrDefault(id, null));


        String streetLine = ChatColor.AQUA + "Street: " + ChatColor.WHITE + displayedStreet;
        String areaLine   = ChatColor.DARK_AQUA + "Area:  " + ChatColor.WHITE + displayedArea;
        String eventLine  = ChatColor.RED + "Event: " + ChatColor.WHITE + displayedEvent;
        String valueLine   = ChatColor.GOLD + "Value (current building):  " + ChatColor.WHITE + displayedValue;

        List<String> newLines = Arrays.asList(cashLine, streetLine, areaLine, eventLine, valueLine);
        List<String> prevLines = lastLines.get(id);
        if (newLines.equals(prevLines)) return;

        try {
            Scoreboard board = p.getScoreboard();
            if (board == null) {
                createFor(p);
                board = p.getScoreboard();
                if (board == null) return;
            }
            Objective obj = board.getObjective("cashhud");
            if (obj == null) {
                try {
                    obj = board.registerNewObjective("cashhud", "test", ChatColor.GOLD + "Information");
                    obj.setDisplaySlot(DisplaySlot.SIDEBAR);
                } catch (Throwable ignored) {}
                if (obj == null) return;
            }

            if (prevLines != null) {
                for (String s : prevLines) {
                    try { board.resetScores(s); } catch (Throwable ignored) {}
                }
            }

            obj.getScore(cashLine).setScore(5);
            obj.getScore(streetLine).setScore(4);
            obj.getScore(areaLine).setScore(3);
            obj.getScore(eventLine).setScore(2);
            obj.getScore(valueLine).setScore(1);

            lastLines.put(id, newLines);
        } catch (Throwable ignored) {}
    }

    private BuildingPolygon findBuildingAtBlockSimple(int wx, int wz) {
        if (plugin.mapData == null || plugin.mapData.buildings == null) return null;
        for (BuildingPolygon bp : plugin.mapData.buildings) {
            if (bp == null) continue;
            try {
                if (bp.contains(wx, wz)) return bp;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private String readAreaFromMaps(long key) {
        if (plugin.mapData == null) return null;
        if (plugin.mapData.landuseMap != null && plugin.mapData.landuseMap.containsKey(key)) {
            return plugin.mapData.landuseMap.get(key);
        }
        if (plugin.mapData.amenityMap != null && plugin.mapData.amenityMap.containsKey(key)) {
            return plugin.mapData.amenityMap.get(key);
        }
        if (plugin.mapData.naturalMap != null && plugin.mapData.naturalMap.containsKey(key)) {
            return plugin.mapData.naturalMap.get(key);
        }
        return null;
    }
}