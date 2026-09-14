package com.guessmarket.api.dto;

import java.util.List;

public record UserSummary(
        String name,
        double balance,
        boolean blocked,
        List<Integer> marketMakerEventIds,
        List<Integer> participatingEventIds) {

    public UserSummary {
        marketMakerEventIds = List.copyOf(marketMakerEventIds);
        participatingEventIds = List.copyOf(participatingEventIds);
    }
}
