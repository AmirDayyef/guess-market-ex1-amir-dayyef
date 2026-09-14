package com.guessmarket.api.dto;

public record TradeReceipt(
        int eventId,
        String userName,
        String optionName,
        long quantity,
        double sharesCost,
        double commission,
        double totalPaid,
        EventDetails updatedEvent,
        UserDetails updatedUser) {
}
