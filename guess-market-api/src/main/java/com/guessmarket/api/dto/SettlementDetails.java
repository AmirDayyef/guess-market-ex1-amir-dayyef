package com.guessmarket.api.dto;

public record SettlementDetails(
        String winningOptionName,
        long winningShares,
        double grossPayout,
        double commission,
        double payoutAfterCommission,
        double finalAccountBalance) {
}

