package com.guessmarket.engine.core;

final class Lmsr {
    private Lmsr() {
    }

    static double cost(long firstShares, long secondShares, int liquidity) {
        double first = (double) firstShares / liquidity;
        double second = (double) secondShares / liquidity;
        double maximum = Math.max(first, second);

        // Log-sum-exp avoids overflow even when many shares are purchased.
        return liquidity * (maximum
                + Math.log(Math.exp(first - maximum) + Math.exp(second - maximum)));
    }

    static double price(long selectedShares, long otherShares, int liquidity) {
        double difference = ((double) otherShares - selectedShares) / liquidity;
        if (difference >= 0) {
            double exponential = Math.exp(-difference);
            return exponential / (1.0 + exponential);
        }
        return 1.0 / (1.0 + Math.exp(difference));
    }
}

