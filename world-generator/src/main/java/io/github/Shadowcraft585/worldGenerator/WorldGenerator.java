package io.github.Shadowcraft585.worldGenerator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.GameRule;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BarFlag;

import java.util.Collections;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.io.File;
import java.io.IOException;

import io.github.Shadowcraft585.worldGenerator.events.*;
import io.github.Shadowcraft585.worldGenerator.turn.TurnManager;
import io.github.Shadowcraft585.worldGenerator.turn.TurnPhase;
import io.github.Shadowcraft585.worldGenerator.cards.*;


public final class WorldGenerator extends JavaPlugin implements Listener, CommandExecutor {

    public MapData mapData;
    public Timer timer;

    public Map<UUID, ZombieMinigame> activeGames = new ConcurrentHashMap<>();

    public PlayerMoney money;

    public PlayerPropertyStore propertyStore;

    public OwnedBuildingsUI buildingsUI;

    public Sidebar sidebar;

    public ChestFillListener chestFillListener;
    public CompassOpenListener compassListener;
    public BuyWoolListener buyWoolListener;

    public TurnManager turnManager;

    public CardRegistry cardRegistry;

    private BossBar phaseBar = null;
    private boolean phaseBarActive = false;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        this.timer = new Timer();

        registerDynamicCommand("start", "Startet den Timer", "/start");
        registerDynamicCommand("time",  "Zeigt die verstrichene Zeit an", "/time");
        registerDynamicCommand("stop",  "Stoppt Timer und Zombie-Minigame", "/stop");
        registerDynamicCommand("money",  "Gibt den Kontostand des Spielers an", "/money");
        registerDynamicCommand("buy",  "Gebäude in dem man sich befindet, kaufen", "/buy");
        registerDynamicCommand("sell",  "Gebäude in dem man sich befindet, verkaufen", "/sell");
        registerDynamicCommand("help",  "Liste aller Commands", "/help");
        registerDynamicCommand("event", "random event auslösen", "/event");
        registerDynamicCommand("monopoly", "starts monopoly minigame", "/monopoly");
        registerDynamicCommand("monopolystop", "stops monopoly minigame", "/monopolystop");
        registerDynamicCommand("forcephase", "forces next monopoly phase", "/forcephase");


        registerTagCommand();

        this.money = new PlayerMoney(this, "money");
        this.propertyStore = new PlayerPropertyStore(this, "owned_buildings");

        this.buildingsUI = new OwnedBuildingsUI(this, this.propertyStore, this.money);
        Bukkit.getPluginManager().registerEvents(this.buildingsUI, this);

        CompassOpenListener compassListener = new CompassOpenListener(this, this.buildingsUI);
        Bukkit.getPluginManager().registerEvents(compassListener, this);

        this.buyWoolListener = new BuyWoolListener(this);
        this.buyWoolListener.registerAndGive();

        this.compassListener = compassListener;

        this.turnManager = new TurnManager(this);
        List<UUID> order = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) order.add(pl.getUniqueId());
        turnManager.setTurnOrder(order);

        this.sidebar = new Sidebar(this);
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { sidebar.createFor(p); } catch (Throwable ignored) {}
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() { try { sidebar.updateAll(); } catch (Throwable ignored) {} }
        }.runTaskTimer(this, 20L, 20L);

        CardRegistry cardRegistry = new CardRegistry();
        cardRegistry.register(new DoubleHouseCard());
        cardRegistry.register(new DrainMoneyCard());
        cardRegistry.register(new StealHouseCard());
        this.cardRegistry = cardRegistry;

        Bukkit.getPluginManager().registerEvents(new CardUseListener(this, this.cardRegistry), this);

        String worldName = XMLReader.getVariable("WorldGenerator", "worldName");

        loadWorld(worldName);
    }

    @Override
    public void onDisable() {
        activeGames.values().forEach(ZombieMinigame::stop);
        activeGames.clear();
    }

    private void loadWorld(String worldName) {
        Bukkit.getScheduler().runTask(this, () -> {
            File worldDir = new File(Bukkit.getWorldContainer(), worldName);

            if (worldDir.exists() && Bukkit.getWorld(worldName) == null) {
                getLogger().info("Found existing world folder '" + worldName + "'. Loading world...");
                try {
                    WorldCreator wc = new WorldCreator(worldName);
                    wc.generateStructures(false);
                    Bukkit.createWorld(wc);

                    getLogger().info("Loaded existing world '" + worldName + "'.");
                } catch (IllegalStateException ise) {
                    getLogger().warning("createWorld not possible right now; retrying.");
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        try {
                            WorldCreator wc = new WorldCreator(worldName);
                            wc.generateStructures(false);
                            Bukkit.createWorld(wc);

                            getLogger().info("Loaded existing world " + worldName + ".");
                        } catch (Exception ex) {
                            getLogger().severe("Failed to load existing world: " + ex.getMessage());
                        }
                    }, 20L);
                } catch (Exception ex) {
                    getLogger().severe("Failed to load existing world: " + ex.getMessage());
                }
            }

            World existing = Bukkit.getWorld(worldName);
            if (existing != null) {
                getLogger().info("World '" + worldName + "' already loaded.");
                return;
            }

            createWorldAsync(worldName);
        });
    }

    private void createWorldAsync(String worldName) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String coordString = XMLReader.getVariable("WorldGenerator", "coordinates");

                String[] coordArray = coordString.split(",");
                double[] coordinates = new double[4];

                for (int i = 0; i < coordArray.length; i++) {
                    coordinates[i] = Double.parseDouble(coordArray[i]);
                }

                MapData loaded = HighwayLoader.load(
                        coordinates[0],
                        coordinates[1],
                        coordinates[2],
                        coordinates[3]
                );

                Bukkit.getScheduler().runTask(this, () -> {
                    this.mapData = loaded;

                    Bukkit.getLogger().info("[WorldGenerator] mapData loaded: buildings=" + (loaded.buildings != null ? loaded.buildings.size() : 0)
                            + ", chestMap=" + (loaded.chestMap != null ? loaded.chestMap.size() : 0)
                            + (loaded.chestMap != null && !loaded.chestMap.isEmpty() ? " sample=" + loaded.chestMap.entrySet().iterator().next() : ""));

                    this.chestFillListener = new ChestFillListener(this,
                            this.mapData.chestMap != null ? this.mapData.chestMap : Collections.emptyMap());
                    Bukkit.getPluginManager().registerEvents(this.chestFillListener, this);

                    if (Bukkit.getWorld(worldName) == null) {
                        try {
                            WorldCreator wc = new WorldCreator(worldName);
                            wc.generator(new CustomChunkGenerator(mapData));
                            Bukkit.createWorld(wc);
                            getLogger().info("Created world '" + worldName + "' with CustomChunkGenerator.");

                        } catch (IllegalStateException ise) {
                            getLogger().warning("createWorld not possible right now, retrying");
                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                try {
                                    WorldCreator wc = new WorldCreator(worldName);
                                    wc.generator(new CustomChunkGenerator(mapData));
                                    Bukkit.createWorld(wc);
                                    getLogger().info("Created world'" + worldName + "' with CustomChunkGenerator.");
                                } catch (Exception ex) {
                                    getLogger().severe("Failed to create world on delayed attempt: " + ex.getMessage());
                                }
                            }, 20L);
                        } catch (Exception ex) {
                            getLogger().severe("Failed to create world: " + ex.getMessage());
                        }
                    } else {
                        getLogger().info("World '" + worldName + "' already exists.");
                    }
                });
            } catch (Exception e) {
                getLogger().severe("Map loading failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender,
                             org.bukkit.command.Command command,
                             String label, String[] args) {

        String name = command.getName().toLowerCase();

        if ("help".equals(name)) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("Dieser Befehl kann nur von einem Spieler ausgeführt werden.");
                return true;
            }

            sender.sendMessage("/start to start the zombie survival minigame");
            sender.sendMessage("/time to see the amount of time you survived");
            sender.sendMessage("/stop to stop the zombie minigame");
            sender.sendMessage("/money to see you balance for the monopoly minigame");
            sender.sendMessage("/buy to buy the building you are standing in");
            sender.sendMessage("/sell to sell the building you are standing in");
            sender.sendMessage("/monopoly to start the monopoly minigame");
            sender.sendMessage("/monopolystop to end the monopoly minigame");
            sender.sendMessage("/forcephase to skip a phase");

            return true;
        }

        if ("tag".equals(name)) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("Dieser Befehl kann nur von einem Spieler ausgeführt werden.");
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;

            if (mapData == null) {
                player.sendMessage("Map data not loaded yet.");
                return true;
            }

            org.bukkit.Location loc = player.getLocation();
            int wx = loc.getBlockX();
            int wz = loc.getBlockZ();
            long key = Raster.keyFor(wx, wz);

            String landuse = mapData.landuseMap != null ? mapData.landuseMap.get(key) : null;
            String natural = mapData.naturalMap != null ? mapData.naturalMap.get(key) : null;
            String amenity = mapData.amenityMap != null ? mapData.amenityMap.get(key) : null;

            player.sendMessage("Landuse: " + (landuse != null ? landuse : "null"));
            player.sendMessage("Natural: " + (natural != null ? natural : "null"));
            player.sendMessage("Amenity: " + (amenity != null ? amenity : "null"));

            return true;
        }

        if ("start".equals(name)) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("Nur Spieler können den Timer starten.");
                return true;
            }
            Player player = (Player) sender;


            timer.start(player);

            UUID id = player.getUniqueId();
            if (activeGames.containsKey(id)) {
                player.sendMessage("Zombie-Minigame läuft bereits.");
            } else {
                ZombieMinigame game = new ZombieMinigame(this, player);
                game.start();
                activeGames.put(id, game);
                player.sendMessage("Timer und Zombie-Minigame gestartet.");
            }
            return true;
        }

        if ("stop".equals(name)) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("Nur Spieler können den Timer/Minigame stoppen.");
                return true;
            }
            Player player = (Player) sender;

            UUID id = player.getUniqueId();
            ZombieMinigame game = activeGames.get(id);
            if (game != null) {
                game.stop();
                activeGames.remove(id);
                player.sendMessage("Zombie-Minigame gestoppt.");
            } else {
                player.sendMessage("Kein laufendes Zombie-Minigame gefunden.");
            }

            try {
                Method stopMethod = timer.getClass().getMethod("stop", Player.class);
                stopMethod.invoke(timer, player);
                player.sendMessage("Timer gestoppt.");
            } catch (NoSuchMethodException nsme) {
            } catch (Exception ex) {
                getLogger().warning("Fehler beim Stoppen des Timers: " + ex.getMessage());
                ex.printStackTrace();
            }

            return true;
        }

        if ("time".equals(name)) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("Dieser Befehl kann nur von einem Spieler ausgeführt werden.");
                return true;
            }
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;

            String timeSurvived = timer.getTime(player);
            player.sendMessage("Deine Zeit: " + timeSurvived);
            return true;
        }

        if ("money".equals(name)) {
            if (!(sender instanceof Player)) { sender.sendMessage("Nur Spieler."); return true; }
            Player p = (Player) sender;
            double bal = money.getBalance(p);
            sender.sendMessage("Dein Kontostand beträgt " + Math.round(bal));
            return true;
        }

        if ("buy".equals(name)) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("Nur Spieler können Gebäude kaufen.");
                return true;
            }

            Player p = (Player) sender;

            if (!turnManager.isBuyPhaseFor(p.getUniqueId())) {
                p.sendMessage("You can only buy/sell during your buy phase.");
                return true;
            }

            if (turnManager.hasPlayerActed(p.getUniqueId())) {
                p.sendMessage("You already performed an action this turn.");
                return true;
            }

            int wx = p.getLocation().getBlockX();
            int wz = p.getLocation().getBlockZ();

            BuildingPolygon target = null;
            for (BuildingPolygon polygon : this.mapData.buildings) {
                if (polygon != null && polygon.contains(wx, wz)) { target = polygon; break; }
            }
            if (target == null) {
                p.sendMessage("Du stehst in keinem Gebäude.");
                return true;
            }

            String bid = PlayerPropertyStore.idFor(target);
            if (propertyStore != null && propertyStore.owns(p, bid)) {
                p.sendMessage("Du besitzt dieses Gebäude bereits.");
                return true;
            }

            double price = target.value;
            double bal = money.getBalance(p);

            if (bal < price) {
                p.sendMessage("Nicht genug Geld. Preis: " + Math.round(price) + "  Du hast: " + Math.round(bal));
                return true;
            }

            this.money.withdraw(p, price);

            if (propertyStore != null) propertyStore.addOwnership(p, bid);

            p.sendMessage("Kauf erfolgreich! Du hast das Gebäude für " + Math.round(price) + " gekauft.");

            turnManager.markPlayerActed(p.getUniqueId());

            if (this.turnManager != null) {
                this.turnManager.endBuyPhase();
            }

            return true;
        }

        if ("sell".equals(name)) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage("Nur Spieler können Gebäude kaufen.");
                return true;
            }

            Player p = (Player) sender;

            if (!turnManager.isBuyPhaseFor(p.getUniqueId())) {
                p.sendMessage("You can only buy/sell during your buy phase.");
                return true;
            }

            if (turnManager.hasPlayerActed(p.getUniqueId())) {
                p.sendMessage("You already performed an action this turn.");
                return true;
            }

            int wx = p.getLocation().getBlockX();
            int wz = p.getLocation().getBlockZ();

            BuildingPolygon target = null;
            for (BuildingPolygon polygon : this.mapData.buildings) {
                if (polygon != null && polygon.contains(wx, wz)) { target = polygon; break; }
            }
            if (target == null) {
                p.sendMessage("Du stehst in keinem Gebäude.");
                return true;
            }

            String bid = PlayerPropertyStore.idFor(target);
            if (propertyStore != null && !propertyStore.owns(p, bid)) {
                p.sendMessage("Du besitzt dieses Gebäude nicht.");
                return true;
            }

            double price = target.value;
            double bal = money.getBalance(p);

            this.money.deposit(p, price);

            if (propertyStore != null) propertyStore.removeOwnership(p, bid);

            p.sendMessage("Verkauf erfolgreich! Du hast das Gebäude für " + Math.round(price) + " verkauft.");

            turnManager.markPlayerActed(p.getUniqueId());

            if (this.turnManager != null) {
                this.turnManager.endBuyPhase();
            }

            return true;
        }

        if ("event".equals(name)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Nur Spieler.");
                return true;
            }
            Player p = (Player) sender;

            World world = p.getWorld();
            List<Player> players = new ArrayList<>(world.getPlayers());
            Random rng = new Random();

            RandomEventContext context = new RandomEventContext(this, world, players, rng);

            List<RandomEvent> events = Arrays.asList(
                    new ConstructionEvent(),
                    new EarthquakeEvent(),
                    new InflationEvent(),
                    new PlagueEvent(),
                    new RenovationEvent(),
                    new TreasureChestEvent()
            );

            RandomEvent ev = events.get(rng.nextInt(events.size()));

            ev.run(context);

            return true;
        }

        if ("monopoly".equals(name)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Nur Spieler.");
                return true;
            }

            if (this.turnManager == null) this.turnManager = new TurnManager(this);

            turnManager.resetMonopolyState();

            TurnManager.phase = TurnPhase.DRAW_CARD;

            List<UUID> order = new ArrayList<>();
            for (Player pl : Bukkit.getOnlinePlayers()) order.add(pl.getUniqueId());

            this.turnManager.setTurnOrder(order);

            startPhaseBar();

            this.turnManager.forceNextPhase();

            sender.sendMessage("Monopoly started. Turn order: " + order.size() + " players.");

            return true;
        }

        if ("monopolystop".equals(name)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Nur Spieler.");
                return true;
            }

            if (turnManager == null) {
                sender.sendMessage("Turn manager not initialized.");
                return true;
            }

            if (turnManager.isRunning()) {
                turnManager.stopMonopoly();
                sender.sendMessage("Monopoly minigame stopped.");
            } else {
                sender.sendMessage("Monopoly minigame not running.");
            }

            stopPhaseBar();
            
            return true;
        }

        if ("forcephase".equals(name)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Nur Spieler.");
                return true;
            }

            if (turnManager == null) {
                sender.sendMessage("Turn manager not initialized.");
                return true;
            }

            try {
                turnManager.forceNextPhase();
                sender.sendMessage("Advanced one phase.");
            } catch (java.lang.Exception e) {
                throw new RuntimeException(e);
            }

            return true;
        }

        return false;
    }

    private void registerDynamicCommand(String name, String description,
                                        String usage) {
        try {
            Constructor<PluginCommand> constructor = PluginCommand.class
                    .getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance(name, this);

            command.setDescription(description);
            command.setUsage(usage);

            command.setExecutor(this);

            Method getCommandMap = getServer().getClass().getMethod("getCommandMap");
            Object commandMapObj = getCommandMap.invoke(getServer());
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapObj;
            commandMap.register(getDescription().getName(), command);

            getLogger().info("Registered command: " + name);
        } catch (Exception e) {
            getLogger().severe("Failed to register command '" + name + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerTagCommand() {
        try {
            Constructor<PluginCommand> command = PluginCommand.class
                    .getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
            command.setAccessible(true);
            PluginCommand tagCmd = command.newInstance("tag", this);

            tagCmd.setDescription("Show OSM tags at your location");
            tagCmd.setUsage("/tag");
            tagCmd.setExecutor(this);

            Method getCommandMap = getServer().getClass().getMethod("getCommandMap");
            Object commandMapObj = getCommandMap.invoke(getServer());

            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapObj;
            commandMap.register(getDescription().getName(), tagCmd);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        World target = Bukkit.getWorld("custom_world");
        if (target == null) {
            p.sendMessage("Custom world not available yet.");
            return;
        }

        if (compassListener != null) {
            ItemStack compass = compassListener.makeOpenCompass();
            boolean has = false;
            for (ItemStack it : p.getInventory().getContents()) {
                if (it == null || it.getType() != Material.COMPASS) continue;
                if (it.getItemMeta() != null && it.getItemMeta().getPersistentDataContainer().has(
                        new NamespacedKey(this, "open_buildings_compass"), PersistentDataType.STRING)) {
                    has = true; break;
                }
            }
            if (!has) {
                p.getInventory().addItem(compass);
            }
        }

        if (target != null) {
            target.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

            long desired = 18000L;
            target.setFullTime(desired);
            target.setTime(desired);
            p.sendMessage("Night set");
        }

        int spawnX;
        int spawnZ;

        String spawnXString = XMLReader.getVariable("WorldGenerator", "spawnX");
        try {
            spawnX = Integer.parseInt(spawnXString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String spawnZString = XMLReader.getVariable("WorldGenerator", "spawnZ");
        try {
            spawnZ = Integer.parseInt(spawnZString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        int spawnY = target.getHighestBlockYAt(spawnX, spawnZ) + 1;

        Location spawn = new Location(target, spawnX, spawnY, spawnZ);
        p.teleport(spawn);

        if (this.money != null && !this.money.hasBalance(p)) {
            double startBalance = Integer.parseInt(XMLReader.getVariable("Monopoly", "startingMoney"));
            this.money.setBalance(p, startBalance);
            p.sendMessage("Startguthaben: " + startBalance);
        }

        p.sendMessage("/help for a list of all commands");

        if (sidebar != null) {
            try { sidebar.createFor(p); } catch (Throwable ignored) {}
        }

        if (phaseBarActive && phaseBar != null) {
            Bukkit.getScheduler().runTask(this, () -> phaseBar.addPlayer(p));
        }

        if (this.buyWoolListener != null) {
            try {
                this.buyWoolListener.giveWool(p);
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();

        String finalTime = "";
        try {
            finalTime = timer.getTime(p);
            timer.stop(p);
        } catch (Exception ignored) { }

        p.sendMessage("Your final time was " + finalTime);

        World target = Bukkit.getWorld("custom_world");

        activeGames.values().forEach(ZombieMinigame::stop);
        activeGames.clear();

        if (target != null) {
            for (Entity e : new ArrayList<>(target.getEntities())) {
                if (e instanceof Player) continue;
                e.remove();
            }

            if (this.chestFillListener != null) {
                this.chestFillListener.resetAllChests();
            }
        }

    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        World target = Bukkit.getWorld("custom_world");
        if (target == null) return;

        int spawnX, spawnZ;

        spawnX = Integer.parseInt(XMLReader.getVariable("WorldGenerator", "spawnX"));
        spawnZ = Integer.parseInt(XMLReader.getVariable("WorldGenerator", "spawnZ"));

        int spawnY = target.getHighestBlockYAt(spawnX, spawnZ) + 1;
        Location spawn = new Location(target, spawnX, spawnY, spawnZ);

        event.setRespawnLocation(spawn);

        p.teleport(spawn);

    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (sidebar != null) {
            try { sidebar.removeFor(event.getPlayer()); } catch (Throwable ignored) {}
        }
        if (phaseBarActive && phaseBar != null) {
            Bukkit.getScheduler().runTask(this, () -> phaseBar.removePlayer(p));
        }
    }

    public PlayerMoney getPlayerMoney() {
        return this.money;
    }

    public void startPhaseBar() {
        if (phaseBarActive) return;
        Bukkit.getScheduler().runTask(this, () -> {
            if (phaseBar != null) {
                phaseBar.removeAll();
                phaseBar = null;
            }
            phaseBar = Bukkit.createBossBar("Phase: starting", BarColor.BLUE, BarStyle.SOLID);
            phaseBar.setVisible(true);
            phaseBarActive = true;
            for (Player p : Bukkit.getOnlinePlayers()) phaseBar.addPlayer(p);
        });
    }

    public void stopPhaseBar() {
        if (!phaseBarActive) return;
        Bukkit.getScheduler().runTask(this, () -> {
            if (phaseBar != null) {
                phaseBar.removeAll();
                phaseBar = null;
            }
            phaseBarActive = false;
        });
    }

    public void updatePhaseBar(String title, double progress, BarColor color) {
        if (!phaseBarActive || phaseBar == null) return;
        double p = Math.max(0.0, Math.min(1.0, progress));
        Bukkit.getScheduler().runTask(this, () -> {
            if (phaseBar == null) return;
            phaseBar.setTitle(title);
            phaseBar.setProgress(p);
            if (color != null) phaseBar.setColor(color);
        });
    }

    public TurnManager getTurnManager() {
        return this.turnManager;
    }
}