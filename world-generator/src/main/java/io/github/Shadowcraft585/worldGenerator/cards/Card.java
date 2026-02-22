package io.github.Shadowcraft585.worldGenerator.cards;

import io.github.Shadowcraft585.worldGenerator.events.RandomEventContext;

public interface Card {
    String getKey();
    String getDisplayName();
    String getDescription();

    default boolean canUse(CardContext context) { return true; }

    void apply(CardContext context);
}