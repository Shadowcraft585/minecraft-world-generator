package io.github.Shadowcraft585.worldGenerator.events;

import io.github.Shadowcraft585.worldGenerator.WorldGenerator;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class RandomEventContext {
    public final WorldGenerator plugin;
    public final World world;
    public final List<Player> players;
    public final Random rng;

    public RandomEventContext(WorldGenerator plugin, World world, List<Player> players, Random rng) {
        this.plugin = plugin;
        this.world = world;
        this.players = (players == null) ? List.of() : List.copyOf(players);
        this.rng = (rng == null) ? new Random() : rng;
    }

    public Optional<Player> randomPlayer() {
        if (players.isEmpty()) return Optional.empty();
        return Optional.of(players.get(rng.nextInt(players.size())));
    }
}