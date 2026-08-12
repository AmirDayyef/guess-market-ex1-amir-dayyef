package com.guessmarket.engine.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MarketState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<Integer, MarketEvent> events;

    public MarketState(List<MarketEvent> events) {
        this.events = new LinkedHashMap<>();
        for (MarketEvent event : events) {
            this.events.put(event.getId(), event);
        }
    }

    public MarketEvent getEvent(int eventId) {
        return events.get(eventId);
    }

    public Collection<MarketEvent> getEvents() {
        return List.copyOf(events.values());
    }
}
