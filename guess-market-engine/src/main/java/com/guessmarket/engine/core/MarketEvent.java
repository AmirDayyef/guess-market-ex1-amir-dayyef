package com.guessmarket.engine.core;

import com.guessmarket.api.dto.CommissionType;
import com.guessmarket.api.exception.InvalidOperationException;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class MarketEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercentage;
    private final CommissionType commissionType;
    private final int liquidity;
    private final List<MarketOption> options;
    private final List<Trade> trades = new ArrayList<>();
    private double accountBalance;
    private double totalCommissionCollected;
    private boolean active = true;
    private int winningOptionIndex = -1;
    private double closingCommission;
    private double payoutAfterCommission;

    public MarketEvent(
            int id,
            String name,
            String description,
            int commissionPercentage,
            CommissionType commissionType,
            int liquidity,
            List<String> optionNames) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercentage = commissionPercentage;
        this.commissionType = commissionType;
        this.liquidity = liquidity;
        this.options = optionNames.stream().map(MarketOption::new).toList();
        // Appendix A defines C(0,0) as the initial subsidy held by the event account.
        this.accountBalance = Lmsr.cost(0L, 0L, this.liquidity);
    }

    public Trade buyShares(int optionNumber, long quantity) {
        if (!active) {
            throw new InvalidOperationException("Event " + id + " is already closed and cannot accept new trades.");
        }
        int selectedIndex = toOptionIndex(optionNumber);
        if (quantity <= 0) {
            throw new InvalidOperationException("Share quantity must be a positive whole number.");
        }

        MarketOption selected = options.get(selectedIndex);
        long oldFirst = options.get(0).getSharesBought();
        long oldSecond = options.get(1).getSharesBought();
        long newSelectedShares;
        try {
            newSelectedShares = Math.addExact(selected.getSharesBought(), quantity);
        } catch (ArithmeticException exception) {
            throw new InvalidOperationException("The requested quantity is too large.");
        }

        long newFirst = selectedIndex == 0 ? newSelectedShares : oldFirst;
        long newSecond = selectedIndex == 1 ? newSelectedShares : oldSecond;
        double sharesCost = Lmsr.cost(newFirst, newSecond, liquidity)
                - Lmsr.cost(oldFirst, oldSecond, liquidity);

        if (!Double.isFinite(sharesCost) || sharesCost < -0.0000001) {
            throw new InvalidOperationException("The requested trade could not be calculated safely.");
        }
        sharesCost = Math.max(0.0, sharesCost);
        double commission = commissionType == CommissionType.ON_PURCHASE
                ? sharesCost * commissionPercentage / 100.0
                : 0.0;

        selected.addShares(quantity);
        Trade trade = new Trade(trades.size() + 1L, selectedIndex, quantity, sharesCost, commission);
        trades.add(trade);
        accountBalance += trade.totalPaid();
        totalCommissionCollected += commission;
        return trade;
    }

    public void close(int winningOptionNumber) {
        if (!active) {
            throw new InvalidOperationException("Event " + id + " is already closed.");
        }
        winningOptionIndex = toOptionIndex(winningOptionNumber);
        double grossPayout = options.get(winningOptionIndex).getSharesBought();
        closingCommission = commissionType == CommissionType.ON_CLOSE
                ? grossPayout * commissionPercentage / 100.0
                : 0.0;
        payoutAfterCommission = grossPayout - closingCommission;
        totalCommissionCollected += closingCommission;
        accountBalance -= payoutAfterCommission;
        active = false;
    }

    public double priceFor(int optionIndex) {
        int otherIndex = optionIndex == 0 ? 1 : 0;
        return Lmsr.price(
                options.get(optionIndex).getSharesBought(),
                options.get(otherIndex).getSharesBought(),
                liquidity);
    }

    private int toOptionIndex(int optionNumber) {
        if (optionNumber < 1 || optionNumber > options.size()) {
            throw new InvalidOperationException(
                    "Option number must be between 1 and " + options.size() + ".");
        }
        return optionNumber - 1;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCommissionPercentage() {
        return commissionPercentage;
    }

    public CommissionType getCommissionType() {
        return commissionType;
    }

    public List<MarketOption> getOptions() {
        return List.copyOf(options);
    }

    public List<Trade> getTrades() {
        return List.copyOf(trades);
    }

    public double getAccountBalance() {
        return accountBalance;
    }

    public double getTotalCommissionCollected() {
        return totalCommissionCollected;
    }

    public boolean isActive() {
        return active;
    }

    public int getWinningOptionIndex() {
        return winningOptionIndex;
    }

    public double getClosingCommission() {
        return closingCommission;
    }

    public double getPayoutAfterCommission() {
        return payoutAfterCommission;
    }
}
