package com.guessmarket.api.exception;

public class MarketNotLoadedException extends GuessMarketException {
    public MarketNotLoadedException() {
        super("No market data is currently loaded. Load an XML file first.");
    }
}

