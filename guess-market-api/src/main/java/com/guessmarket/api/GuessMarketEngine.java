package com.guessmarket.api;

import com.guessmarket.api.dto.EventDetails;
import com.guessmarket.api.dto.EventSummary;
import com.guessmarket.api.dto.TradeReceipt;

import java.util.List;

/**
 * The public boundary between a user interface and the Guess Market engine.
 */
public interface GuessMarketEngine {
    void loadMarketFromXml(String filePath);

    boolean hasLoadedMarket();

    List<EventSummary> getEvents();

    List<EventSummary> getActiveEvents();

    EventDetails getEventDetails(int eventId);

    TradeReceipt buyShares(int eventId, int optionNumber, long quantity);

    EventDetails closeEvent(int eventId, int winningOptionNumber);

    void saveState(String filePathWithoutExtension);

    void loadState(String filePathWithoutExtension);
}

