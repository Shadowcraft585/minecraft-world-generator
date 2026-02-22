package io.github.Shadowcraft585.worldGenerator.turn;

import io.github.Shadowcraft585.worldGenerator.events.RandomEvent;

public class ActiveEvent {
    public final RandomEvent event;
    public int remainingTurns;

    public ActiveEvent(RandomEvent event, int durationTurns) {
        this.event = event;
        this.remainingTurns = durationTurns;
    }
}