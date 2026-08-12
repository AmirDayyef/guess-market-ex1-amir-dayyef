package com.guessmarket.api.dto;

public record OptionDetails(
        int number,
        String name,
        double currentPrice,
        long sharesBought,
        boolean winner) {
}

