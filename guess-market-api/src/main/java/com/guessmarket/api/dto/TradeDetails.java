package com.guessmarket.api.dto;

public record TradeDetails(
        long sequenceNumber,
        String optionName,
        long quantity,
        double sharesCost,
        double commission,
        double totalPaid) {
}

