package com.guessmarket.engine.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MarketState implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    private final String loadedFilePath;
    private final Map<Integer, MarketEvent> events;
    private final Map<String, MarketUser> users;

    public MarketState(String loadedFilePath, List<MarketEvent> events, List<MarketUser> users) {
        this.loadedFilePath = loadedFilePath;
        this.events = new LinkedHashMap<>();
        this.users = new LinkedHashMap<>();
        for (MarketEvent event : events) {
            this.events.put(event.getId(), event);
        }
        for (MarketUser user : users) {
            this.users.put(user.getName().toLowerCase(Locale.ROOT), user);
        }
    }

    public MarketEvent getEvent(int eventId) {
        return events.get(eventId);
    }

    public Collection<MarketEvent> getEvents() {
        return List.copyOf(events.values());
    }

    public MarketUser getUser(String userName) {
        return userName == null ? null : users.get(userName.trim().toLowerCase(Locale.ROOT));
    }

    public Collection<MarketUser> getUsers() {
        return List.copyOf(users.values());
    }

    public String getLoadedFilePath() {
        return loadedFilePath;
    }
}
