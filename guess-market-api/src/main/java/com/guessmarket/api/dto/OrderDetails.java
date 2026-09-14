package com.guessmarket.api.dto;

public record OrderDetails(
        long sequenceNumber,
        String userName,
        int optionNumber,
        String optionName,
        OrderSide side,
        long quantity,
        double price) {
}
