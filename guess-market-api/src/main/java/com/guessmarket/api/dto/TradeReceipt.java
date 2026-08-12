package com.guessmarket.api.dto;

public record TradeReceipt(
        int eventId,
        String optionName,
        long quantity,
        double sharesCost,
        double commission,
        double totalPaid,
        EventDetails updatedEvent) {
}

