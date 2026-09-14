package com.guessmarket.engine.core;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

public final class UserPosition implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int eventId;
    private final long[] holdings = new long[2];
    private final double[] amountPaid = new double[2];
    private double commissionPaid;
    private double cashFlow;

    public UserPosition(int eventId) {
        this.eventId = eventId;
    }

    public void buy(int optionIndex, long quantity, double value, double commission) {
        holdings[optionIndex] = Math.addExact(holdings[optionIndex], quantity);
        amountPaid[optionIndex] += value;
        commissionPaid += commission;
        cashFlow -= value + commission;
    }

    public void receiveInitialShares(int optionIndex, long quantity) {
        holdings[optionIndex] = Math.addExact(holdings[optionIndex], quantity);
    }

    public void sell(int optionIndex, long quantity, double value) {
        if (quantity > holdings[optionIndex]) {
            throw new IllegalArgumentException("Cannot sell more shares than the user owns.");
        }
        double averageCost = holdings[optionIndex] == 0
                ? 0.0
                : amountPaid[optionIndex] / holdings[optionIndex];
        holdings[optionIndex] -= quantity;
        amountPaid[optionIndex] = Math.max(0.0, amountPaid[optionIndex] - averageCost * quantity);
        cashFlow += value;
    }

    public void settle(double payout, double commission) {
        commissionPaid += commission;
        cashFlow += payout - commission;
        Arrays.fill(holdings, 0L);
        Arrays.fill(amountPaid, 0.0);
    }

    public int getEventId() { return eventId; }
    public long getHolding(int optionIndex) { return holdings[optionIndex]; }
    public double getAmountPaid(int optionIndex) { return amountPaid[optionIndex]; }
    public double getCommissionPaid() { return commissionPaid; }
    public double getProfitLoss() { return cashFlow; }
}
