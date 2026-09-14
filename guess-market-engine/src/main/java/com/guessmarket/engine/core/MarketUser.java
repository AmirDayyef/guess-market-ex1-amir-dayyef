package com.guessmarket.engine.core;

import com.guessmarket.api.exception.InvalidOperationException;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MarketUser implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final Set<Integer> marketMakerEventIds;
    private final Map<Integer, UserPosition> positions = new LinkedHashMap<>();
    private double balance;
    private boolean blocked;

    public MarketUser(String name, double initialBalance, Collection<Integer> marketMakerEventIds) {
        this.name = name;
        this.balance = initialBalance;
        this.marketMakerEventIds = new LinkedHashSet<>(marketMakerEventIds);
    }

    public void ensureCanAct() {
        if (blocked) {
            throw new InvalidOperationException(
                    "User " + name + " is blocked because the account balance became negative.");
        }
    }

    public void debit(double amount) {
        balance -= amount;
        if (balance < -0.0000001) {
            blocked = true;
        }
    }

    public void debitForOpening(double amount) {
        if (balance + 0.0000001 < amount) {
            throw new InvalidOperationException(
                    "User " + name + " does not have enough money to open this event. Required: "
                            + String.format("%.2f", amount) + ", available: " + String.format("%.2f", balance) + ".");
        }
        balance -= amount;
    }

    public void credit(double amount) {
        balance += amount;
    }

    public UserPosition positionFor(int eventId) {
        return positions.computeIfAbsent(eventId, UserPosition::new);
    }

    public UserPosition getPosition(int eventId) {
        return positions.get(eventId);
    }

    public String getName() { return name; }
    public double getBalance() { return balance; }
    public boolean isBlocked() { return blocked; }
    public Set<Integer> getMarketMakerEventIds() { return Set.copyOf(marketMakerEventIds); }
    public List<UserPosition> getPositions() { return List.copyOf(positions.values()); }
}
