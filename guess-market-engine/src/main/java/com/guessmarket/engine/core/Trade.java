package com.guessmarket.engine.core;

import com.guessmarket.api.dto.TradeKind;

import java.io.Serializable;

public record Trade(
        long sequenceNumber,
        TradeKind kind,
        int optionIndex,
        long quantity,
        double pricePerShare,
        double sharesCost,
        double commission,
        String buyerName,
        String sellerName) implements Serializable {

    public double totalPaid() {
        return sharesCost + commission;
    }
}
