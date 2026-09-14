package com.guessmarket.api;

import com.guessmarket.api.dto.EventDetails;
import com.guessmarket.api.dto.EventSummary;
import com.guessmarket.api.dto.OrderReceipt;
import com.guessmarket.api.dto.OrderSide;
import com.guessmarket.api.dto.TradeReceipt;
import com.guessmarket.api.dto.UserDetails;
import com.guessmarket.api.dto.UserSummary;

import java.util.List;

/**
 * The public boundary between a user interface and the Guess Market engine.
 */
public interface GuessMarketEngine {
    void loadMarketFromXml(String filePath);

    boolean hasLoadedMarket();

    String getLoadedFilePath();

    List<EventSummary> getEvents();

    List<EventSummary> getActiveEvents();

    List<UserSummary> getUsers();

    UserDetails getUserDetails(String userName);

    EventDetails getEventDetails(int eventId);

    EventDetails openEvent(String userName, int eventId);

    TradeReceipt buyShares(String userName, int eventId, int optionNumber, long quantity);

    OrderReceipt submitOrder(
            String userName,
            int eventId,
            int optionNumber,
            OrderSide side,
            long quantity,
            double price);

    EventDetails closeEvent(String userName, int eventId, int winningOptionNumber);

    void saveState(String filePathWithoutExtension);

    void loadState(String filePathWithoutExtension);
}
