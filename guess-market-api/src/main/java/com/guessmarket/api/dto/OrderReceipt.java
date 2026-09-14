package com.guessmarket.api.dto;

import java.util.List;

public record OrderReceipt(
        String message,
        List<TradeDetails> executions,
        EventDetails updatedEvent,
        UserDetails updatedUser) {

    public OrderReceipt {
        executions = List.copyOf(executions);
    }
}
