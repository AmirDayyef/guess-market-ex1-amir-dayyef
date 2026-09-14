package com.guessmarket.engine.core;

import com.guessmarket.api.dto.OrderSide;

import java.io.Serial;
import java.io.Serializable;

public final class MarketOrder implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long sequenceNumber;
    private final String userName;
    private final int optionIndex;
    private final OrderSide side;
    private final double price;
    private long remainingQuantity;

    public MarketOrder(
            long sequenceNumber,
            String userName,
            int optionIndex,
            OrderSide side,
            long quantity,
            double price) {
        this.sequenceNumber = sequenceNumber;
        this.userName = userName;
        this.optionIndex = optionIndex;
        this.side = side;
        this.remainingQuantity = quantity;
        this.price = price;
    }

    public void fill(long quantity) {
        if (quantity <= 0 || quantity > remainingQuantity) {
            throw new IllegalArgumentException("Invalid order fill quantity.");
        }
        remainingQuantity -= quantity;
    }

    public boolean isFilled() {
        return remainingQuantity == 0;
    }

    public long getSequenceNumber() { return sequenceNumber; }
    public String getUserName() { return userName; }
    public int getOptionIndex() { return optionIndex; }
    public OrderSide getSide() { return side; }
    public double getPrice() { return price; }
    public long getRemainingQuantity() { return remainingQuantity; }
}
