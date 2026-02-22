package io.github.Shadowcraft585.worldGenerator.cards;

import java.util.*;

public class CardRegistry {
    private Map<String, Card> map = new LinkedHashMap<>();
    private Random rng = new Random();

    public void register(Card c) { map.put(c.getKey(), c); }

    public Card get(String key) { return map.get(key); }

    public List<Card> all() { return new ArrayList<>(map.values()); }

    public Optional<Card> pickRandom() {
        if (map.isEmpty()) return Optional.empty();
        List<Card> l = all();
        return Optional.of(l.get(rng.nextInt(l.size())));
    }
}