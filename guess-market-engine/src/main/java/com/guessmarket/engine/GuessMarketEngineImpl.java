package com.guessmarket.engine;

import com.guessmarket.api.GuessMarketEngine;
import com.guessmarket.api.dto.EventDetails;
import com.guessmarket.api.dto.EventStatus;
import com.guessmarket.api.dto.EventSummary;
import com.guessmarket.api.dto.OptionDetails;
import com.guessmarket.api.dto.SettlementDetails;
import com.guessmarket.api.dto.TradeDetails;
import com.guessmarket.api.dto.TradeReceipt;
import com.guessmarket.api.exception.InvalidOperationException;
import com.guessmarket.api.exception.MarketNotLoadedException;
import com.guessmarket.api.exception.StatePersistenceException;
import com.guessmarket.engine.core.MarketEvent;
import com.guessmarket.engine.core.MarketOption;
import com.guessmarket.engine.core.MarketState;
import com.guessmarket.engine.core.Trade;
import com.guessmarket.engine.xml.XmlMarketLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class GuessMarketEngineImpl implements GuessMarketEngine {
    private static final String STATE_EXTENSION = ".gmstate";

    private final XmlMarketLoader xmlLoader;
    private MarketState state;

    public GuessMarketEngineImpl() {
        this(new XmlMarketLoader());
    }

    GuessMarketEngineImpl(XmlMarketLoader xmlLoader) {
        this.xmlLoader = xmlLoader;
    }

    @Override
    public void loadMarketFromXml(String filePath) {
        // Conversion is completed before replacing state, so a bad file never
        // destroys the last valid market.
        List<MarketEvent> loadedEvents = xmlLoader.load(filePath);
        state = new MarketState(loadedEvents);
    }

    @Override
    public boolean hasLoadedMarket() {
        return state != null;
    }

    @Override
    public List<EventSummary> getEvents() {
        return requireState().getEvents().stream().map(this::toSummary).toList();
    }

    @Override
    public List<EventSummary> getActiveEvents() {
        return requireState().getEvents().stream()
                .filter(MarketEvent::isActive)
                .map(this::toSummary)
                .toList();
    }

    @Override
    public EventDetails getEventDetails(int eventId) {
        return toDetails(requireEvent(eventId));
    }

    @Override
    public TradeReceipt buyShares(int eventId, int optionNumber, long quantity) {
        MarketEvent event = requireEvent(eventId);
        Trade trade = event.buyShares(optionNumber, quantity);
        String optionName = event.getOptions().get(trade.optionIndex()).getName();
        return new TradeReceipt(
                eventId,
                optionName,
                trade.quantity(),
                trade.sharesCost(),
                trade.commission(),
                trade.totalPaid(),
                toDetails(event));
    }

    @Override
    public EventDetails closeEvent(int eventId, int winningOptionNumber) {
        MarketEvent event = requireEvent(eventId);
        event.close(winningOptionNumber);
        return toDetails(event);
    }

    @Override
    public void saveState(String filePathWithoutExtension) {
        MarketState currentState = requireState();
        Path target = statePath(filePathWithoutExtension);
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new StatePersistenceException("The destination directory does not exist: " + parent);
        }

        Path temporary;
        try {
            temporary = Files.createTempFile(parent, "guess-market-", ".tmp");
        } catch (IOException exception) {
            throw new StatePersistenceException("Could not create a temporary state file: " + exception.getMessage(), exception);
        }

        try {
            try (OutputStream output = Files.newOutputStream(temporary);
                 ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
                objectOutput.writeObject(currentState);
            }
            try {
                Files.move(temporary, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            tryDelete(temporary);
            throw new StatePersistenceException("The market state could not be saved: " + exception.getMessage(), exception);
        }
    }

    @Override
    public void loadState(String filePathWithoutExtension) {
        Path source = statePath(filePathWithoutExtension);
        if (!Files.isRegularFile(source)) {
            throw new StatePersistenceException("The saved state file does not exist: " + source);
        }

        final MarketState loadedState;
        try (InputStream input = Files.newInputStream(source);
             ObjectInputStream objectInput = new ObjectInputStream(input)) {
            Object loaded = objectInput.readObject();
            if (!(loaded instanceof MarketState marketState)) {
                throw new StatePersistenceException("The selected file is not a Guess Market state file.");
            }
            loadedState = marketState;
        } catch (IOException | ClassNotFoundException exception) {
            throw new StatePersistenceException(
                    "The market state could not be loaded. The file may be damaged or incompatible: "
                            + exception.getMessage(), exception);
        }

        // Replace current data only after the whole state was read successfully.
        state = loadedState;
    }

    private MarketState requireState() {
        if (state == null) {
            throw new MarketNotLoadedException();
        }
        return state;
    }

    private MarketEvent requireEvent(int eventId) {
        MarketEvent event = requireState().getEvent(eventId);
        if (event == null) {
            throw new InvalidOperationException("Event ID " + eventId + " does not exist in the loaded market.");
        }
        return event;
    }

    private EventSummary toSummary(MarketEvent event) {
        return new EventSummary(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getCommissionPercentage(),
                event.getCommissionType(),
                event.getOptions().stream().map(MarketOption::getName).toList(),
                event.isActive() ? EventStatus.ACTIVE : EventStatus.CLOSED);
    }

    private EventDetails toDetails(MarketEvent event) {
        List<MarketOption> marketOptions = event.getOptions();
        List<OptionDetails> options = new ArrayList<>();
        for (int index = 0; index < marketOptions.size(); index++) {
            MarketOption option = marketOptions.get(index);
            options.add(new OptionDetails(
                    index + 1,
                    option.getName(),
                    event.priceFor(index),
                    option.getSharesBought(),
                    !event.isActive() && event.getWinningOptionIndex() == index));
        }

        List<TradeDetails> history = event.getTrades().stream()
                .sorted(Comparator.comparingLong(Trade::sequenceNumber).reversed())
                .map(trade -> new TradeDetails(
                        trade.sequenceNumber(),
                        marketOptions.get(trade.optionIndex()).getName(),
                        trade.quantity(),
                        trade.sharesCost(),
                        trade.commission(),
                        trade.totalPaid()))
                .toList();

        SettlementDetails settlement = null;
        if (!event.isActive()) {
            MarketOption winner = marketOptions.get(event.getWinningOptionIndex());
            double grossPayout = winner.getSharesBought();
            settlement = new SettlementDetails(
                    winner.getName(),
                    winner.getSharesBought(),
                    grossPayout,
                    event.getClosingCommission(),
                    event.getPayoutAfterCommission(),
                    event.getAccountBalance());
        }

        return new EventDetails(
                toSummary(event),
                event.getAccountBalance(),
                event.getTotalCommissionCollected(),
                options,
                history,
                settlement);
    }

    private Path statePath(String pathWithoutExtension) {
        if (pathWithoutExtension == null || pathWithoutExtension.isBlank()) {
            throw new StatePersistenceException("The saved-state path cannot be empty.");
        }
        String value = pathWithoutExtension.trim();
        if (!value.toLowerCase(Locale.ROOT).endsWith(STATE_EXTENSION)) {
            value += STATE_EXTENSION;
        }
        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw new StatePersistenceException("The supplied state path is not valid: " + exception.getReason(), exception);
        }
    }

    private void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the useful original exception if cleanup also fails.
        }
    }
}
