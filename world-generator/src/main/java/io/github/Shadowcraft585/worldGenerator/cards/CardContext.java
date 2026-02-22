package io.github.Shadowcraft585.worldGenerator.cards;

import io.github.Shadowcraft585.worldGenerator.WorldGenerator;

import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.Optional;
import java.util.UUID;

public class CardContext {
    public WorldGenerator plugin;
    public World world;
    public Player player;
    public UUID actorId;
    public List<Player> onlinePlayers;
    public Random rng;

    public CardContext(WorldGenerator plugin, World world, Player player, UUID actorId,
                       List<Player> onlinePlayers, Random rng) {
        this.plugin = plugin;
        this.world = world;
        this.player = player;
        this.actorId = actorId;
        this.onlinePlayers = onlinePlayers;
        this.rng = rng == null ? new Random() : rng;
    }

    public Optional<Player> randomOtherPlayer() {
        if (onlinePlayers == null || onlinePlayers.isEmpty()) return Optional.empty();
        List<Player> others = onlinePlayers.stream().filter(p -> !p.getUniqueId().equals(player.getUniqueId())).toList();
        if (others.isEmpty()) return Optional.empty();
        return Optional.of(others.get(rng.nextInt(others.size())));
    }

    public Optional<Player> actorAsPlayer() {
        if (player != null) return Optional.of(player);
        if (actorId != null) return Optional.ofNullable(plugin.getServer().getPlayer(actorId));
        return Optional.empty();
    }
}