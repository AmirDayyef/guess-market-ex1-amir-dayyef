package com.guessmarket.api.exception;

public class GuessMarketException extends RuntimeException {
    public GuessMarketException(String message) {
        super(message);
    }

    public GuessMarketException(String message, Throwable cause) {
        super(message, cause);
    }
}

