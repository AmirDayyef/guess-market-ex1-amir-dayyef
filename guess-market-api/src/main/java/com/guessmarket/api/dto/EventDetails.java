package com.guessmarket.api.dto;

import java.util.List;

public record EventDetails(
        EventSummary summary,
        double totalCommissionCollected,
        Integer liquidity,
        Boolean mintAllowed,
        Integer initialInvestment,
        Integer baseValue,
        List<OptionDetails> options,
        List<OrderDetails> orderBook,
        List<ParticipantDetails> participants,
        List<TradeDetails> tradeHistory,
        SettlementDetails settlement) {

    public EventDetails {
        options = List.copyOf(options);
        orderBook = List.copyOf(orderBook);
        participants = List.copyOf(participants);
        tradeHistory = List.copyOf(tradeHistory);
    }
}
