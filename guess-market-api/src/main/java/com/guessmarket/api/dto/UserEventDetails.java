package com.guessmarket.api.dto;

import java.util.List;

public record UserEventDetails(
        int eventId,
        String eventName,
        MarketMethod marketMethod,
        EventStatus status,
        boolean marketMaker,
        List<HoldingDetails> holdings,
        List<TradeDetails> tradeHistory,
        double commissionPaid,
        double profitLoss) {

    public UserEventDetails {
        holdings = List.copyOf(holdings);
        tradeHistory = List.copyOf(tradeHistory);
    }
}
