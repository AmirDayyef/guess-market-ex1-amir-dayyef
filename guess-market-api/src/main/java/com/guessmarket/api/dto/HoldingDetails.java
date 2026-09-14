package com.guessmarket.api.dto;

public record HoldingDetails(
        int optionNumber,
        String optionName,
        long shares,
        double amountPaid) {
}
