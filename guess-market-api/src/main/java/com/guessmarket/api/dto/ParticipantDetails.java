package com.guessmarket.api.dto;

import java.util.List;

public record ParticipantDetails(
        String userName,
        List<HoldingDetails> holdings,
        double commissionPaid,
        double profitLoss,
        boolean hasOpenOrders) {

    public ParticipantDetails {
        holdings = List.copyOf(holdings);
    }
}
