package com.guessmarket.api.exception;

public class StatePersistenceException extends GuessMarketException {
    public StatePersistenceException(String message) {
        super(message);
    }

    public StatePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
