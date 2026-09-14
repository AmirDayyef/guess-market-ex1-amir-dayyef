package com.guessmarket.api.dto;

public record TradeDetails(
        long sequenceNumber,
        TradeKind kind,
        String optionName,
        long quantity,
        double pricePerShare,
        double sharesCost,
        double commission,
        double totalPaid,
        String buyerName,
        String sellerName) {
}
