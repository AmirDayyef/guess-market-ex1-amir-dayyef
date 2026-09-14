package com.guessmarket.api.dto;

public enum MarketMethod {
    LMSR("LMSR"),
    ORDER_BOOK("Order Book");

    private final String displayName;

    MarketMethod(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
