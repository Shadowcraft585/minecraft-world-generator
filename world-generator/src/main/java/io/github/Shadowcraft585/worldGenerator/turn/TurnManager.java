package io.github.Shadowcraft585.worldGenerator.turn;

import io.github.Shadowcraft585.worldGenerator.WorldGenerator;
import io.github.Shadowcraft585.worldGenerator.events.RandomEvent;
import io.github.Shadowcraft585.worldGenerator.events.RandomEventContext;
import io.github.Shadowcraft585.worldGenerator.BuildingPolygon;
import io.github.Shadowcraft585.worldGenerator.PlayerPropertyStore;
import io.github.Shadowcraft585.worldGenerator.XMLReader;
import io.github.Shadowcraft585.worldGenerator.cards.*;
import io.github.Shadowcraft585.worldGenerator.events.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.stream.Collectors;

public class TurnManager {

    private final WorldGenerator plugin;
    private final List<UUID> turnOrder = new ArrayList<>();
    private int currentIndex = 0;
    public static TurnPhase phase = TurnPhase.DRAW_CARD;
    private Map<UUID, Double> aiBalances = new HashMap<>();
    private Map<UUID, Set<String>> aiOwnership = new HashMap<>();

    private final List<ActiveEvent> activeEvents = new ArrayList<>();
    private final Random rng = new Random();

    private boolean running = false;

    private final Set<UUID> actedThisTurn = new HashSet<>();

    private BukkitTask phaseTimeoutTask = null;
    private long buyTimeoutTicks = 20L * 60L;

    public static boolean RUNNING = true;

    public TurnManager(WorldGenerator plugin) {
        this.plugin = plugin;
    }

    public void setTurnOrder(List<UUID> players) {
        RUNNING = true;
        turnOrder.clear();
        turnOrder.addAll(players);
        UUID ai = UUID.randomUUID();
        turnOrder.add(ai);
        aiBalances.putIfAbsent(ai, getAiStartingBalance());
        aiOwnership.putIfAbsent(ai, new HashSet<>());
        currentIndex = 0;
    }

    public UUID getCurrentPlayer() {
        if (turnOrder.isEmpty()) return null;
        return turnOrder.get(currentIndex % turnOrder.size());
    }

    public TurnPhase getPhase() { return phase; }

    public boolean isBuyPhaseFor(UUID player) {
        UUID cur = getCurrentPlayer();
        return cur != null && cur.equals(player) && phase == TurnPhase.BUY_PHASE;
    }

    public void proceedPhase() {
        if (!RUNNING) return;

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::proceedPhase);
            return;
        }

        plugin.getLogger().info("ProceedPhase called. Current phase = " + phase + ", playerIndex=" + currentIndex);
        UUID current = getCurrentPlayer();

        switch (phase) {
            case DRAW_CARD: {
                try {
                    handlePlayerDraw();
                } catch (Throwable t) {
                    plugin.getLogger().info("handlePlayerDraw() error: " + t.getMessage());
                    t.printStackTrace();
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    this.phase = TurnPhase.BUY_PHASE;
                    this.proceedPhase();
                }, 20L * 5L);
                break;
            }

            case BUY_PHASE: {
                startBuyPhaseForCurrentPlayer();
                break;
            }

            case END_PHASE: {
                try {
                    plugin.updatePhaseBar("Proceeding with end of turn effects...", 1.0, BarColor.RED);
                    handleEndPhase();
                } catch (Throwable t) {
                    plugin.getLogger().warning("handleEndPhase() error: " + t.getMessage());
                    t.printStackTrace();
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    advanceToNextPlayer();
                    phase = TurnPhase.DRAW_CARD;
                    proceedPhase();
                }, 20L * 5L);
                break;
            }
        }
    }

    public void notifyPlayersPhase() {
        Bukkit.getOnlinePlayers().forEach(p -> {
            UUID cur = getCurrentPlayer();
            boolean yourTurn = (cur != null && cur.equals(p.getUniqueId()));
            p.sendMessage("Phase: " + phase + (yourTurn ? " (your turn)" : ""));
        });
    }

    public void handlePlayerDraw() {
        UUID cur = getCurrentPlayer();
        String label = getActorLabel(cur);

        if (label != "AI") {
            plugin.updatePhaseBar("Draw Phase: " + label, 0.33, BarColor.GREEN);

            if (cur == null) return;
            Player p = plugin.getServer().getPlayer(cur);
            if (p == null) return;

            Optional<Card> cards = plugin.cardRegistry.pickRandom();
            if (cards.isPresent()) {
                Card c = cards.get();
                ItemStack paper = CardItemUtil.makeCardPaper(plugin, c);
                if (p != null) p.getInventory().addItem(paper);
                if (p != null) p.sendMessage("You drew a card: " + c.getDisplayName());
            }
        } else {
            plugin.updatePhaseBar("Draw Phase: " + label, 0.33, BarColor.PURPLE);

            Optional<Card> cards = plugin.cardRegistry.pickRandom();
            if (cards.isPresent()) {
                Card card = cards.get();
                World w = plugin.getServer().getWorld("custom_world");
                List<Player> online = (w == null) ? List.of() : new ArrayList<>(w.getPlayers());
                CardContext context = new CardContext(plugin, w, null, cur, online, rng);

                try {
                    if (card.canUse(context)) {
                        card.apply(context);
                        plugin.getLogger().info("AI " + cur + " used card " + card.getKey());
                    } else {
                        plugin.getLogger().info("AI card not usable at the moment: " + card.getKey());
                    }
                } catch (Throwable t) {
                    plugin.getLogger().info("AI card execution failed: " + t);
                    t.printStackTrace();
                }
            }
        }
    }

    public void startBuyPhaseForCurrentPlayer() {
        notifyPlayersPhase();

        if (phaseTimeoutTask != null) {
            phaseTimeoutTask.cancel();
            phaseTimeoutTask = null;
        }

        UUID current = getCurrentPlayer();
        String label = getActorLabel(current);

        if (label != "AI") {
            plugin.updatePhaseBar("Buy Phase: " + label + " has 60 seconds", 0.66, BarColor.GREEN);

            phaseTimeoutTask = new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        UUID cur = getCurrentPlayer();
                        Player p = cur == null ? null : plugin.getServer().getPlayer(cur);
                        if (p != null) p.sendMessage("Your buy phase timed out, ending turn.");
                        endBuyPhase();
                    });
                }
            }.runTaskLater(plugin, buyTimeoutTicks);
        } else {
            plugin.updatePhaseBar("Buy Phase: " + label, 0.66, BarColor.PURPLE);

            double balance = aiBalances.getOrDefault(current, getAiStartingBalance());
            List<BuildingPolygon> all = (plugin.mapData != null) ? plugin.mapData.buildings : Collections.emptyList();
            if (all.isEmpty()) {
                plugin.getLogger().info("No buildings for AI to buy.");
                return;
            }

            int attempts = 0;
            BuildingPolygon chosen = null;
            while (attempts < 30) {
                attempts++;
                BuildingPolygon cand = all.get(rng.nextInt(all.size()));
                if (cand == null) continue;
                if (isOwnedByAnyone(cand)) continue;
                double price = cand.getValue();
                if (price <= 0) continue;
                if (balance >= price) {
                    chosen = cand;
                    break;
                }
            }

            if (chosen != null) {
                double price = chosen.getValue();
                balance -= price;
                aiBalances.put(current, balance);
                String id = PlayerPropertyStore.idFor(chosen);
                aiOwnership.computeIfAbsent(current, k -> new HashSet<>()).add(id);

                Bukkit.broadcastMessage("An AI bought a property for " + Math.round(price));
            } else {
                plugin.getLogger().info("AI did not find affordable unowned property after attempts.");
            }
            markPlayerActed(current);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                endBuyPhase();
            }, 20L * 5L);
        }
    }

    public void endBuyPhase() {
        if (this.phase != TurnPhase.BUY_PHASE) return;

        if (phaseTimeoutTask != null) {
            phaseTimeoutTask.cancel();
            phaseTimeoutTask = null;
        }

        this.phase = TurnPhase.END_PHASE;

        Bukkit.getScheduler().runTask(plugin, this::proceedPhase);
    }

    private void handleEndPhase() {
        Iterator<ActiveEvent> it = activeEvents.iterator();
        while (it.hasNext()) {
            ActiveEvent ae = it.next();
            ae.remainingTurns--;
            if (ae.remainingTurns <= 0) {
                it.remove();
                Bukkit.broadcastMessage("Global event has ended");
            }
        }

        UUID current = getCurrentPlayer();
        if (current != null) {
            double totalIncome = 0.0;

            Map<String, BuildingPolygon> idMap = new HashMap<>();

            for (BuildingPolygon building : plugin.mapData.buildings) {
                if (building == null) continue;
                String id = PlayerPropertyStore.idFor(building);
                if (id != null && !id.isEmpty()) idMap.put(id, building);
            }

            Player player = plugin.getServer().getPlayer(current);
            if (player != null) {

                Set<String> owned = plugin.propertyStore.getOwnedIds(player);
                for (String id : owned) {
                    BuildingPolygon ownedBuilding = idMap.get(id);
                    if (ownedBuilding != null) totalIncome += ownedBuilding.getEarning();
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {

                }, 20L * 2L);

                player.sendMessage("earned " + Math.round(totalIncome) + "$ this turn");
                plugin.getPlayerMoney().deposit(player, totalIncome);
            } else {
                Set<String> ownedAi = getAiOwnership(current);
                for (String id : ownedAi) {
                    BuildingPolygon ownedBuilding = idMap.get(id);
                    if (ownedBuilding != null) totalIncome += ownedBuilding.getEarning();
                }
                if (totalIncome > 0.0) {
                    double prev = aiBalances.getOrDefault(current, getAiStartingBalance());
                    aiBalances.put(current, prev + totalIncome);
                    Bukkit.broadcastMessage("AI received income: " + Math.round(totalIncome));
                }
            }
        }

        if (activeEvents.size() == 0) {
            if (rng.nextDouble() < 0.2) {
                triggerRandomEvent();
            }
        }

        Bukkit.broadcastMessage("End of turn");
    }

    public void triggerRandomEvent() {
        List<RandomEvent> events = Arrays.asList(
                new ConstructionEvent(),
                new EarthquakeEvent(),
                new InflationEvent(),
                new PlagueEvent(),
                new RenovationEvent(),
                new TreasureChestEvent()
        );

        RandomEvent ev = events.get(rng.nextInt(events.size()));
        int duration = 1 + rng.nextInt(4);

        activeEvents.add(new ActiveEvent(ev, duration));

        World w = plugin.getServer().getWorld("custom_world");
        List<Player> players = (w == null) ? List.of() : new ArrayList<>(w.getPlayers());
        RandomEventContext context = new RandomEventContext(plugin, w, players, rng);

        ev.run(context);


    }

    public void advanceToNextPlayer() {
        currentIndex = (currentIndex + 1) % Math.max(1, turnOrder.size());
        clearActedForNextPlayer();
    }

    public void forceNextPhase() {
        Bukkit.getScheduler().runTask(plugin, this::proceedPhase);
    }

    public void stopMonopoly() {
        RUNNING = false;
        phase = TurnPhase.NOT_STARTED;
    }

    public boolean isRunning() {
        return RUNNING;
    }

    public boolean hasPlayerActed(UUID player) {
        return player == null ? true : actedThisTurn.contains(player);
    }

    public void markPlayerActed(UUID player) {
        if (player != null) actedThisTurn.add(player);
    }

    public void clearActedForNextPlayer() {
        actedThisTurn.clear();
    }

    public double getAiStartingBalance() {
        try {
            String s = XMLReader.getVariable("Monopoly", "startingMoney");
            if (s != null) return Double.parseDouble(s);
        } catch (Exception ignored) {}
        return 10000.0;
    }

    public boolean isOwnedByAnyone(BuildingPolygon b) {
        if (b == null) return false;
        String id = PlayerPropertyStore.idFor(b);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.propertyStore != null && plugin.propertyStore.owns(p, id)) return true;
        }
        for (Set<String> owned : aiOwnership.values()) {
            if (owned.contains(id)) return true;
        }
        return false;
    }

    public Set<String> getAiOwnership(UUID ai) {
        return aiOwnership.getOrDefault(ai, Collections.emptySet());
    }

    public void addAiOwnership(UUID ai, String id) {
        aiOwnership.computeIfAbsent(ai, k->new HashSet<>()).add(id);
    }

    public boolean removeAiOwnership(UUID ai, String id) {
        return aiOwnership.computeIfAbsent(ai, k->new HashSet<>()).remove(id);
    }

    public String getActorLabel(UUID actor) {
        if (actor == null) return "nobody";
        Player p = plugin.getServer().getPlayer(actor);
        if (p != null) return p.getName();
        return "AI";
    }

    public String getActiveEventSummary() {
        if (activeEvents == null || activeEvents.isEmpty()) return "Currently no event";

        ActiveEvent activeEvent = activeEvents.get(0);
        String name = activeEvent.event.getClass().getSimpleName();
        return name;
    }

    public void resetMonopolyState() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::resetMonopolyState);
            return;
        }

        double startBalance = getAiStartingBalance();
        try {
            //Player money/building reset
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.getPlayerMoney().setBalance(p, startBalance);
                plugin.money.setBalance(p, startBalance);

                Set<String> owned = new HashSet<>(plugin.propertyStore.getOwnedIds(p));
                for (String id : owned) {
                    plugin.propertyStore.removeOwnership(p, id);
                }

                //Card reset
                PlayerInventory inv = p.getInventory();
                ItemStack[] contents = inv.getContents();
                boolean changed = false;
                for (int i = 0; i < contents.length; i++) {
                    ItemStack it = contents[i];
                    if (it == null) continue;

                    String cardKey = CardItemUtil.readCardKey(plugin, it);

                    if (cardKey != null) {
                        contents[i] = null;
                        changed = true;
                    }
                }
                if (changed) inv.setContents(contents);

                ItemStack off = inv.getItemInOffHand();
                if (off != null) {
                    try {
                        String offKey = CardItemUtil.readCardKey(plugin, off);
                        if (offKey != null) {
                            inv.setItemInOffHand(null);
                        }
                    } catch (Throwable ignored) {
                    }
                }

            }

            //AI reset
            aiOwnership.clear();
            aiBalances.clear();

        } catch (Throwable t) {
            plugin.getLogger().info("resetMonopolyState() failed: " + t.getMessage());
        }
    }
}