package com.guessmarket.engine.core;

import java.io.Serial;
import java.io.Serializable;

public final class MarketOption implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private long sharesIssued;
    private Double lastTradePrice;

    MarketOption(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public long getSharesIssued() {
        return sharesIssued;
    }

    public void addIssuedShares(long quantity) {
        sharesIssued = Math.addExact(sharesIssued, quantity);
    }

    public Double getLastTradePrice() {
        return lastTradePrice;
    }

    public void setLastTradePrice(double lastTradePrice) {
        this.lastTradePrice = lastTradePrice;
    }
}
