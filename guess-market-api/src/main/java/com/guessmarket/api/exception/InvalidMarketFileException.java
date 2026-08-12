package com.guessmarket.api.exception;

public class InvalidMarketFileException extends GuessMarketException {
    public InvalidMarketFileException(String message) {
        super(message);
    }

    public InvalidMarketFileException(String message, Throwable cause) {
        super(message, cause);
    }
}

