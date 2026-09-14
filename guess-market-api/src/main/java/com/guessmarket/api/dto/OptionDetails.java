package com.guessmarket.api.dto;

public record OptionDetails(
        int number,
        String name,
        Double currentPrice,
        long sharesIssued,
        Double lastTradePrice,
        Double bestBid,
        Double bestAsk,
        Double midPrice,
        Double spread,
        boolean winner) {
}
