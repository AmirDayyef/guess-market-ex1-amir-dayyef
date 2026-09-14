package com.guessmarket.engine.core;

import com.guessmarket.api.dto.CommissionType;
import com.guessmarket.api.dto.EventStatus;
import com.guessmarket.api.dto.MarketMethod;
import com.guessmarket.api.dto.OrderSide;
import com.guessmarket.api.dto.TradeKind;
import com.guessmarket.api.exception.InvalidOperationException;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class MarketEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercentage;
    private final CommissionType commissionType;
    private final MarketMethod marketMethod;
    private final String marketMakerName;
    private final Integer liquidity;
    private final Boolean mintAllowed;
    private final Integer initialInvestment;
    private final Integer baseValue;
    private final List<MarketOption> options;
    private final List<MarketOrder> orders = new ArrayList<>();
    private final List<Trade> trades = new ArrayList<>();

    private EventStatus status = EventStatus.INACTIVE;
    private double accountBalance;
    private double totalCommissionCollected;
    private int winningOptionIndex = -1;
    private double closingCommission;
    private double payoutAfterCommission;
    private double returnedToMarketMaker;
    private long nextOrderSequence = 1L;
    private long nextTradeSequence = 1L;

    private MarketEvent(
            int id,
            String name,
            String description,
            int commissionPercentage,
            CommissionType commissionType,
            MarketMethod marketMethod,
            String marketMakerName,
            Integer liquidity,
            Boolean mintAllowed,
            Integer initialInvestment,
            Integer baseValue,
            List<String> optionNames) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercentage = commissionPercentage;
        this.commissionType = commissionType;
        this.marketMethod = marketMethod;
        this.marketMakerName = marketMakerName;
        this.liquidity = liquidity;
        this.mintAllowed = mintAllowed;
        this.initialInvestment = initialInvestment;
        this.baseValue = baseValue;
        this.options = optionNames.stream().map(MarketOption::new).toList();
    }

    public static MarketEvent lmsr(
            int id,
            String name,
            String description,
            int commissionPercentage,
            CommissionType commissionType,
            int liquidity,
            String marketMakerName,
            List<String> optionNames) {
        return new MarketEvent(
                id, name, description, commissionPercentage, commissionType,
                MarketMethod.LMSR, marketMakerName, liquidity, null, null, 1, optionNames);
    }

    public static MarketEvent orderBook(
            int id,
            String name,
            String description,
            int commissionPercentage,
            CommissionType commissionType,
            boolean mintAllowed,
            int initialInvestment,
            int baseValue,
            String marketMakerName,
            List<String> optionNames) {
        return new MarketEvent(
                id, name, description, commissionPercentage, commissionType,
                MarketMethod.ORDER_BOOK, marketMakerName, null, mintAllowed,
                initialInvestment, baseValue, optionNames);
    }

    public double initialOpeningCost() {
        return marketMethod == MarketMethod.LMSR
                ? Lmsr.cost(0L, 0L, liquidity)
                : initialInvestment;
    }

    public void open() {
        if (status != EventStatus.INACTIVE) {
            throw new InvalidOperationException("Event " + id + " has already been opened.");
        }
        accountBalance = initialOpeningCost();
        status = EventStatus.ACTIVE;
    }

    public Trade executeLmsrPurchase(String userName, int optionNumber, long quantity) {
        ensureActive();
        if (marketMethod != MarketMethod.LMSR) {
            throw new InvalidOperationException("Event " + id + " is not an LMSR event.");
        }
        int optionIndex = toOptionIndex(optionNumber);
        if (quantity <= 0) {
            throw new InvalidOperationException("Share quantity must be a positive whole number.");
        }

        long oldFirst = options.get(0).getSharesIssued();
        long oldSecond = options.get(1).getSharesIssued();
        long newSelected;
        try {
            newSelected = Math.addExact(options.get(optionIndex).getSharesIssued(), quantity);
        } catch (ArithmeticException exception) {
            throw new InvalidOperationException("The requested quantity is too large.");
        }
        long newFirst = optionIndex == 0 ? newSelected : oldFirst;
        long newSecond = optionIndex == 1 ? newSelected : oldSecond;
        double cost = Lmsr.cost(newFirst, newSecond, liquidity) - Lmsr.cost(oldFirst, oldSecond, liquidity);
        if (!Double.isFinite(cost) || cost < -0.0000001) {
            throw new InvalidOperationException("The requested trade could not be calculated safely.");
        }
        cost = Math.max(0.0, cost);
        double commission = purchaseCommission(cost);

        options.get(optionIndex).addIssuedShares(quantity);
        double pricePerShare = cost / quantity;
        options.get(optionIndex).setLastTradePrice(pricePerShare);
        Trade trade = new Trade(
                nextTradeSequence++, TradeKind.LMSR_PURCHASE, optionIndex, quantity,
                pricePerShare, cost, commission, userName, null);
        trades.add(trade);
        accountBalance += cost;
        totalCommissionCollected += commission;
        return trade;
    }

    public MarketOrder createOrder(
            String userName,
            int optionNumber,
            OrderSide side,
            long quantity,
            double price) {
        ensureActive();
        if (marketMethod != MarketMethod.ORDER_BOOK) {
            throw new InvalidOperationException("Event " + id + " is not an Order Book event.");
        }
        if (quantity <= 0) {
            throw new InvalidOperationException("Order quantity must be a positive whole number.");
        }
        if (!Double.isFinite(price) || price <= 0.0 || price > baseValue - 0.01 + 0.0000001) {
            throw new InvalidOperationException(
                    "Order price must be greater than 0 and no higher than "
                            + String.format("%.2f", baseValue - 0.01) + ".");
        }
        return new MarketOrder(nextOrderSequence++, userName, toOptionIndex(optionNumber), side, quantity, price);
    }

    public Trade recordOrderTrade(
            TradeKind kind,
            int optionIndex,
            long quantity,
            double price,
            double commission,
            String buyerName,
            String sellerName) {
        double value = quantity * price;
        MarketOption option = options.get(optionIndex);
        if (kind == TradeKind.MINT) {
            option.addIssuedShares(quantity);
        }
        option.setLastTradePrice(price);
        totalCommissionCollected += commission;
        Trade trade = new Trade(
                nextTradeSequence++, kind, optionIndex, quantity, price, value,
                commission, buyerName, sellerName);
        trades.add(trade);
        return trade;
    }

    public void addMintCollateral(double amount) {
        accountBalance += amount;
    }

    public void issueInitialOrderBookShares(long quantity) {
        if (marketMethod != MarketMethod.ORDER_BOOK) {
            throw new IllegalStateException("Initial order-book shares are only valid for Order Book events.");
        }
        options.forEach(option -> option.addIssuedShares(quantity));
    }

    public void addOrder(MarketOrder order) {
        orders.add(order);
    }

    public void removeFilledOrders() {
        orders.removeIf(MarketOrder::isFilled);
    }

    public void finishSettlement(
            int winningOptionNumber,
            double grossPayout,
            double closingCommission,
            double returnedToMarketMaker) {
        winningOptionIndex = toOptionIndex(winningOptionNumber);
        this.closingCommission = closingCommission;
        this.payoutAfterCommission = grossPayout - closingCommission;
        this.returnedToMarketMaker = returnedToMarketMaker;
        totalCommissionCollected += closingCommission;
        accountBalance = 0.0;
        orders.clear();
        status = EventStatus.CLOSED;
    }

    public double purchaseCommission(double value) {
        return commissionType == CommissionType.ON_PURCHASE
                ? value * commissionPercentage / 100.0
                : 0.0;
    }

    public double closingCommission(double grossPayout) {
        return commissionType == CommissionType.ON_CLOSE
                ? grossPayout * commissionPercentage / 100.0
                : 0.0;
    }

    public double priceFor(int optionIndex) {
        if (marketMethod != MarketMethod.LMSR) {
            return Double.NaN;
        }
        int otherIndex = optionIndex == 0 ? 1 : 0;
        return Lmsr.price(
                options.get(optionIndex).getSharesIssued(),
                options.get(otherIndex).getSharesIssued(),
                liquidity);
    }

    public void ensureActive() {
        if (status != EventStatus.ACTIVE) {
            throw new InvalidOperationException("Event " + id + " is not active.");
        }
    }

    public int toOptionIndex(int optionNumber) {
        if (optionNumber < 1 || optionNumber > options.size()) {
            throw new InvalidOperationException(
                    "Option number must be between 1 and " + options.size() + ".");
        }
        return optionNumber - 1;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCommissionPercentage() { return commissionPercentage; }
    public CommissionType getCommissionType() { return commissionType; }
    public MarketMethod getMarketMethod() { return marketMethod; }
    public String getMarketMakerName() { return marketMakerName; }
    public Integer getLiquidity() { return liquidity; }
    public Boolean getMintAllowed() { return mintAllowed; }
    public Integer getInitialInvestment() { return initialInvestment; }
    public Integer getBaseValue() { return baseValue; }
    public List<MarketOption> getOptions() { return List.copyOf(options); }
    public List<MarketOrder> getOrders() { return orders; }
    public List<Trade> getTrades() { return List.copyOf(trades); }
    public EventStatus getStatus() { return status; }
    public double getAccountBalance() { return accountBalance; }
    public double getTotalCommissionCollected() { return totalCommissionCollected; }
    public int getWinningOptionIndex() { return winningOptionIndex; }
    public double getClosingCommission() { return closingCommission; }
    public double getPayoutAfterCommission() { return payoutAfterCommission; }
    public double getReturnedToMarketMaker() { return returnedToMarketMaker; }
}
