package com.guessmarket.api.dto;

import java.util.List;

public record EventDetails(
        EventSummary summary,
        double accountBalance,
        double totalCommissionCollected,
        List<OptionDetails> options,
        List<TradeDetails> tradeHistory,
        SettlementDetails settlement) {

    public EventDetails {
        options = List.copyOf(options);
        tradeHistory = List.copyOf(tradeHistory);
    }
}

