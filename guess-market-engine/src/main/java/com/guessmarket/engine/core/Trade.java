package com.guessmarket.engine.core;

import java.io.Serial;
import java.io.Serializable;

public record Trade(
        long sequenceNumber,
        int optionIndex,
        long quantity,
        double sharesCost,
        double commission) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public double totalPaid() {
        return sharesCost + commission;
    }
}
