package com.guessmarket.engine.core;

import java.io.Serial;
import java.io.Serializable;

public final class MarketOption implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private long sharesBought;

    MarketOption(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public long getSharesBought() {
        return sharesBought;
    }

    void addShares(long quantity) {
        sharesBought = Math.addExact(sharesBought, quantity);
    }
}
