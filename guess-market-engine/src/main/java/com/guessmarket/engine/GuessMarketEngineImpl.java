package com.guessmarket.engine;

import com.guessmarket.api.GuessMarketEngine;
import com.guessmarket.api.dto.EventDetails;
import com.guessmarket.api.dto.EventStatus;
import com.guessmarket.api.dto.EventSummary;
import com.guessmarket.api.dto.HoldingDetails;
import com.guessmarket.api.dto.MarketMethod;
import com.guessmarket.api.dto.OptionDetails;
import com.guessmarket.api.dto.OrderDetails;
import com.guessmarket.api.dto.OrderReceipt;
import com.guessmarket.api.dto.OrderSide;
import com.guessmarket.api.dto.ParticipantDetails;
import com.guessmarket.api.dto.SettlementDetails;
import com.guessmarket.api.dto.TradeDetails;
import com.guessmarket.api.dto.TradeKind;
import com.guessmarket.api.dto.TradeReceipt;
import com.guessmarket.api.dto.UserDetails;
import com.guessmarket.api.dto.UserEventDetails;
import com.guessmarket.api.dto.UserSummary;
import com.guessmarket.api.exception.InvalidOperationException;
import com.guessmarket.api.exception.MarketNotLoadedException;
import com.guessmarket.api.exception.StatePersistenceException;
import com.guessmarket.engine.core.MarketEvent;
import com.guessmarket.engine.core.MarketOption;
import com.guessmarket.engine.core.MarketOrder;
import com.guessmarket.engine.core.MarketState;
import com.guessmarket.engine.core.MarketUser;
import com.guessmarket.engine.core.Trade;
import com.guessmarket.engine.core.UserPosition;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GuessMarketEngineImpl implements GuessMarketEngine {
    private static final String STATE_EXTENSION = ".gmstate";
    private static final double TOLERANCE = 0.0000001;

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
        // A complete state is validated and created before it replaces the current one.
        state = xmlLoader.load(filePath);
    }

    @Override
    public boolean hasLoadedMarket() {
        return state != null;
    }

    @Override
    public String getLoadedFilePath() {
        return requireState().getLoadedFilePath();
    }

    @Override
    public List<EventSummary> getEvents() {
        return requireState().getEvents().stream().map(this::toSummary).toList();
    }

    @Override
    public List<EventSummary> getActiveEvents() {
        return requireState().getEvents().stream()
                .filter(event -> event.getStatus() == EventStatus.ACTIVE)
                .map(this::toSummary)
                .toList();
    }

    @Override
    public List<UserSummary> getUsers() {
        return requireState().getUsers().stream()
                .map(this::toUserSummary)
                .toList();
    }

    @Override
    public UserDetails getUserDetails(String userName) {
        return toUserDetails(requireUser(userName));
    }

    @Override
    public EventDetails getEventDetails(int eventId) {
        return toDetails(requireEvent(eventId));
    }

    @Override
    public EventDetails openEvent(String userName, int eventId) {
        MarketUser user = requireUser(userName);
        MarketEvent event = requireEvent(eventId);
        user.ensureCanAct();
        ensureMarketMaker(user, event);
        if (event.getStatus() != EventStatus.INACTIVE) {
            throw new InvalidOperationException("Event " + eventId + " has already been opened.");
        }

        double openingCost = event.initialOpeningCost();
        user.debitForOpening(openingCost);
        event.open();

        if (event.getMarketMethod() == MarketMethod.ORDER_BOOK && openingCost > 0.0) {
            long pairs = (long) Math.floor(openingCost / event.getBaseValue());
            if (pairs > 0) {
                event.issueInitialOrderBookShares(pairs);
                UserPosition position = user.positionFor(eventId);
                position.buy(0, pairs, openingCost / 2.0, 0.0);
                position.buy(1, pairs, openingCost / 2.0, 0.0);
            }
        }
        return toDetails(event);
    }

    @Override
    public TradeReceipt buyShares(String userName, int eventId, int optionNumber, long quantity) {
        MarketUser buyer = requireUser(userName);
        MarketEvent event = requireEvent(eventId);
        buyer.ensureCanAct();

        Trade trade = event.executeLmsrPurchase(buyer.getName(), optionNumber, quantity);
        buyer.debit(trade.totalPaid());
        buyer.positionFor(eventId).buy(
                trade.optionIndex(), trade.quantity(), trade.sharesCost(), trade.commission());
        requireUser(event.getMarketMakerName()).credit(trade.commission());

        return new TradeReceipt(
                eventId,
                buyer.getName(),
                event.getOptions().get(trade.optionIndex()).getName(),
                trade.quantity(),
                trade.sharesCost(),
                trade.commission(),
                trade.totalPaid(),
                toDetails(event),
                toUserDetails(buyer));
    }

    @Override
    public OrderReceipt submitOrder(
            String userName,
            int eventId,
            int optionNumber,
            OrderSide side,
            long quantity,
            double price) {
        MarketUser user = requireUser(userName);
        MarketEvent event = requireEvent(eventId);
        user.ensureCanAct();
        if (side == null) {
            throw new InvalidOperationException("An order must be either BUY or SELL.");
        }

        MarketOrder incoming = event.createOrder(user.getName(), optionNumber, side, quantity, price);
        if (side == OrderSide.SELL) {
            validateAvailableSharesForSale(event, user, incoming);
        }

        List<Trade> executions = new ArrayList<>();
        matchAgainstSameOption(event, incoming, executions);
        if (!incoming.isFilled() && side == OrderSide.BUY && Boolean.TRUE.equals(event.getMintAllowed())) {
            matchByMinting(event, incoming, executions);
        }
        event.removeFilledOrders();
        if (!incoming.isFilled()) {
            event.addOrder(incoming);
        }

        String message;
        if (executions.isEmpty()) {
            message = "The order was added to the order book.";
        } else if (incoming.isFilled()) {
            message = "The order was fully executed in " + executions.size() + " trade(s).";
        } else {
            message = "The order was partially executed; " + incoming.getRemainingQuantity()
                    + " share(s) remain in the order book.";
        }
        return new OrderReceipt(
                message,
                executions.stream().map(trade -> toTradeDetails(event, trade)).toList(),
                toDetails(event),
                toUserDetails(user));
    }

    @Override
    public EventDetails closeEvent(String userName, int eventId, int winningOptionNumber) {
        MarketUser marketMaker = requireUser(userName);
        MarketEvent event = requireEvent(eventId);
        marketMaker.ensureCanAct();
        ensureMarketMaker(marketMaker, event);
        event.ensureActive();
        int winnerIndex = event.toOptionIndex(winningOptionNumber);
        double payoutPerShare = event.getMarketMethod() == MarketMethod.ORDER_BOOK
                ? event.getBaseValue()
                : 1.0;

        double totalGrossPayout = 0.0;
        double totalClosingCommission = 0.0;
        for (MarketUser user : requireState().getUsers()) {
            UserPosition position = user.getPosition(eventId);
            if (position == null) {
                continue;
            }
            double grossPayout = position.getHolding(winnerIndex) * payoutPerShare;
            double commission = event.closingCommission(grossPayout);
            if (grossPayout > 0.0) {
                user.credit(grossPayout - commission);
                marketMaker.credit(commission);
            }
            position.settle(grossPayout, commission);
            totalGrossPayout += grossPayout;
            totalClosingCommission += commission;
        }

        double remaining = event.getAccountBalance() - totalGrossPayout;
        if (remaining < -TOLERANCE) {
            throw new InvalidOperationException(
                    "Event " + eventId + " does not have enough collateral to pay the winners.");
        }
        double returnedToMarketMaker = Math.max(0.0, remaining);
        marketMaker.credit(returnedToMarketMaker);
        event.finishSettlement(
                winningOptionNumber,
                totalGrossPayout,
                totalClosingCommission,
                returnedToMarketMaker);
        return toDetails(event);
    }

    private void validateAvailableSharesForSale(
            MarketEvent event,
            MarketUser user,
            MarketOrder incoming) {
        UserPosition position = user.getPosition(event.getId());
        long owned = position == null ? 0L : position.getHolding(incoming.getOptionIndex());
        long alreadyOffered = event.getOrders().stream()
                .filter(order -> order.getSide() == OrderSide.SELL)
                .filter(order -> order.getOptionIndex() == incoming.getOptionIndex())
                .filter(order -> order.getUserName().equalsIgnoreCase(user.getName()))
                .mapToLong(MarketOrder::getRemainingQuantity)
                .sum();
        if (incoming.getRemainingQuantity() > owned - alreadyOffered) {
            throw new InvalidOperationException(
                    "User " + user.getName() + " does not own enough uncommitted shares for this sell order.");
        }
    }

    private void matchAgainstSameOption(
            MarketEvent event,
            MarketOrder incoming,
            List<Trade> executions) {
        Comparator<MarketOrder> pricePriority = incoming.getSide() == OrderSide.BUY
                ? Comparator.comparingDouble(MarketOrder::getPrice)
                    .thenComparingLong(MarketOrder::getSequenceNumber)
                : Comparator.comparingDouble(MarketOrder::getPrice).reversed()
                    .thenComparingLong(MarketOrder::getSequenceNumber);

        List<MarketOrder> candidates = event.getOrders().stream()
                .filter(order -> order.getOptionIndex() == incoming.getOptionIndex())
                .filter(order -> order.getSide() != incoming.getSide())
                .filter(order -> pricesCross(incoming, order))
                .sorted(pricePriority)
                .toList();

        for (MarketOrder resting : candidates) {
            if (incoming.isFilled()) {
                break;
            }
            long quantity = Math.min(incoming.getRemainingQuantity(), resting.getRemainingQuantity());
            String buyerName = incoming.getSide() == OrderSide.BUY
                    ? incoming.getUserName()
                    : resting.getUserName();
            String sellerName = incoming.getSide() == OrderSide.SELL
                    ? incoming.getUserName()
                    : resting.getUserName();
            executions.add(executeResale(
                    event, incoming.getOptionIndex(), quantity, resting.getPrice(), buyerName, sellerName));
            incoming.fill(quantity);
            resting.fill(quantity);
        }
    }

    private boolean pricesCross(MarketOrder incoming, MarketOrder resting) {
        return incoming.getSide() == OrderSide.BUY
                ? incoming.getPrice() + TOLERANCE >= resting.getPrice()
                : incoming.getPrice() <= resting.getPrice() + TOLERANCE;
    }

    private Trade executeResale(
            MarketEvent event,
            int optionIndex,
            long quantity,
            double price,
            String buyerName,
            String sellerName) {
        MarketUser buyer = requireUser(buyerName);
        MarketUser seller = requireUser(sellerName);
        double value = quantity * price;
        double commission = event.purchaseCommission(value);

        buyer.debit(value + commission);
        seller.credit(value);
        buyer.positionFor(event.getId()).buy(optionIndex, quantity, value, commission);
        seller.positionFor(event.getId()).sell(optionIndex, quantity, value);
        requireUser(event.getMarketMakerName()).credit(commission);
        return event.recordOrderTrade(
                TradeKind.ORDER_MATCH, optionIndex, quantity, price, commission,
                buyer.getName(), seller.getName());
    }

    private void matchByMinting(
            MarketEvent event,
            MarketOrder incoming,
            List<Trade> executions) {
        int oppositeOption = incoming.getOptionIndex() == 0 ? 1 : 0;
        List<MarketOrder> candidates = event.getOrders().stream()
                .filter(order -> order.getSide() == OrderSide.BUY)
                .filter(order -> order.getOptionIndex() == oppositeOption)
                .filter(order -> order.getPrice() + incoming.getPrice() + TOLERANCE >= event.getBaseValue())
                .sorted(Comparator.comparingDouble(MarketOrder::getPrice).reversed()
                        .thenComparingLong(MarketOrder::getSequenceNumber))
                .toList();

        for (MarketOrder resting : candidates) {
            if (incoming.isFilled()) {
                break;
            }
            long quantity = Math.min(incoming.getRemainingQuantity(), resting.getRemainingQuantity());
            double restingPrice = resting.getPrice();
            double incomingPrice = event.getBaseValue() - restingPrice;

            executions.add(executeMintLeg(
                    event, incoming.getUserName(), incoming.getOptionIndex(), quantity, incomingPrice));
            executions.add(executeMintLeg(
                    event, resting.getUserName(), resting.getOptionIndex(), quantity, restingPrice));
            event.addMintCollateral(quantity * event.getBaseValue());
            incoming.fill(quantity);
            resting.fill(quantity);
        }
    }

    private Trade executeMintLeg(
            MarketEvent event,
            String buyerName,
            int optionIndex,
            long quantity,
            double price) {
        MarketUser buyer = requireUser(buyerName);
        double value = quantity * price;
        double commission = event.purchaseCommission(value);
        buyer.debit(value + commission);
        buyer.positionFor(event.getId()).buy(optionIndex, quantity, value, commission);
        requireUser(event.getMarketMakerName()).credit(commission);
        return event.recordOrderTrade(
                TradeKind.MINT, optionIndex, quantity, price, commission, buyer.getName(), null);
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

    private MarketUser requireUser(String userName) {
        if (userName == null || userName.isBlank()) {
            throw new InvalidOperationException("A user must be selected.");
        }
        MarketUser user = requireState().getUser(userName);
        if (user == null) {
            throw new InvalidOperationException("User " + userName.trim() + " does not exist in the loaded market.");
        }
        return user;
    }

    private void ensureMarketMaker(MarketUser user, MarketEvent event) {
        if (!event.getMarketMakerName().equalsIgnoreCase(user.getName())) {
            throw new InvalidOperationException(
                    "Only " + event.getMarketMakerName() + ", the event's Market Maker, may perform this action.");
        }
    }

    private EventSummary toSummary(MarketEvent event) {
        return new EventSummary(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getCommissionPercentage(),
                event.getCommissionType(),
                event.getOptions().stream().map(MarketOption::getName).toList(),
                event.getStatus(),
                event.getMarketMethod(),
                event.getMarketMakerName(),
                event.getAccountBalance());
    }

    private EventDetails toDetails(MarketEvent event) {
        List<OptionDetails> options = new ArrayList<>();
        for (int index = 0; index < event.getOptions().size(); index++) {
            MarketOption option = event.getOptions().get(index);
            Double bestBid = bestPrice(event, index, OrderSide.BUY);
            Double bestAsk = bestPrice(event, index, OrderSide.SELL);
            Double mid = bestBid == null || bestAsk == null ? null : (bestBid + bestAsk) / 2.0;
            Double spread = bestBid == null || bestAsk == null ? null : bestAsk - bestBid;
            Double currentPrice;
            if (event.getMarketMethod() == MarketMethod.LMSR) {
                currentPrice = event.priceFor(index);
            } else {
                currentPrice = option.getLastTradePrice() != null ? option.getLastTradePrice() : mid;
            }
            options.add(new OptionDetails(
                    index + 1,
                    option.getName(),
                    currentPrice,
                    option.getSharesIssued(),
                    option.getLastTradePrice(),
                    bestBid,
                    bestAsk,
                    mid,
                    spread,
                    event.getStatus() == EventStatus.CLOSED && event.getWinningOptionIndex() == index));
        }

        List<OrderDetails> orderBook = event.getOrders().stream()
                .sorted(orderDisplayComparator())
                .map(order -> new OrderDetails(
                        order.getSequenceNumber(),
                        order.getUserName(),
                        order.getOptionIndex() + 1,
                        event.getOptions().get(order.getOptionIndex()).getName(),
                        order.getSide(),
                        order.getRemainingQuantity(),
                        order.getPrice()))
                .toList();

        Set<String> participantNames = new HashSet<>();
        for (MarketUser user : requireState().getUsers()) {
            if (user.getPosition(event.getId()) != null) {
                participantNames.add(user.getName().toLowerCase(Locale.ROOT));
            }
        }
        event.getOrders().forEach(order -> participantNames.add(order.getUserName().toLowerCase(Locale.ROOT)));

        List<ParticipantDetails> participants = requireState().getUsers().stream()
                .filter(user -> participantNames.contains(user.getName().toLowerCase(Locale.ROOT)))
                .map(user -> toParticipantDetails(user, event))
                .toList();

        List<TradeDetails> history = event.getTrades().stream()
                .sorted(Comparator.comparingLong(Trade::sequenceNumber).reversed())
                .map(trade -> toTradeDetails(event, trade))
                .toList();

        SettlementDetails settlement = null;
        if (event.getStatus() == EventStatus.CLOSED) {
            MarketOption winner = event.getOptions().get(event.getWinningOptionIndex());
            settlement = new SettlementDetails(
                    winner.getName(),
                    winner.getSharesIssued(),
                    event.getPayoutAfterCommission() + event.getClosingCommission(),
                    event.getClosingCommission(),
                    event.getPayoutAfterCommission(),
                    event.getReturnedToMarketMaker(),
                    event.getAccountBalance());
        }

        return new EventDetails(
                toSummary(event),
                event.getTotalCommissionCollected(),
                event.getLiquidity(),
                event.getMintAllowed(),
                event.getInitialInvestment(),
                event.getBaseValue(),
                options,
                orderBook,
                participants,
                history,
                settlement);
    }

    private Comparator<MarketOrder> orderDisplayComparator() {
        return Comparator.comparingInt(MarketOrder::getOptionIndex)
                .thenComparing(MarketOrder::getSide)
                .thenComparing((first, second) -> {
                    int priceComparison = Double.compare(first.getPrice(), second.getPrice());
                    return first.getSide() == OrderSide.BUY ? -priceComparison : priceComparison;
                })
                .thenComparingLong(MarketOrder::getSequenceNumber);
    }

    private Double bestPrice(MarketEvent event, int optionIndex, OrderSide side) {
        return event.getOrders().stream()
                .filter(order -> order.getOptionIndex() == optionIndex && order.getSide() == side)
                .map(MarketOrder::getPrice)
                .reduce(side == OrderSide.BUY ? Math::max : Math::min)
                .orElse(null);
    }

    private ParticipantDetails toParticipantDetails(MarketUser user, MarketEvent event) {
        UserPosition position = user.getPosition(event.getId());
        List<HoldingDetails> holdings = holdingDetails(event, position);
        boolean hasOpenOrders = event.getOrders().stream()
                .anyMatch(order -> order.getUserName().equalsIgnoreCase(user.getName()));
        return new ParticipantDetails(
                user.getName(),
                holdings,
                position == null ? 0.0 : position.getCommissionPaid(),
                position == null ? 0.0 : position.getProfitLoss(),
                hasOpenOrders);
    }

    private UserSummary toUserSummary(MarketUser user) {
        List<Integer> participating = user.getPositions().stream()
                .map(UserPosition::getEventId)
                .sorted()
                .toList();
        return new UserSummary(
                user.getName(),
                user.getBalance(),
                user.isBlocked(),
                user.getMarketMakerEventIds().stream().sorted().toList(),
                participating);
    }

    private UserDetails toUserDetails(MarketUser user) {
        Set<Integer> eventIds = new HashSet<>(user.getMarketMakerEventIds());
        user.getPositions().forEach(position -> eventIds.add(position.getEventId()));
        List<UserEventDetails> events = eventIds.stream()
                .sorted()
                .map(this::requireEvent)
                .map(event -> {
                    UserPosition position = user.getPosition(event.getId());
                    List<TradeDetails> trades = event.getTrades().stream()
                            .filter(trade -> user.getName().equalsIgnoreCase(trade.buyerName())
                                    || user.getName().equalsIgnoreCase(trade.sellerName()))
                            .sorted(Comparator.comparingLong(Trade::sequenceNumber).reversed())
                            .map(trade -> toTradeDetails(event, trade))
                            .toList();
                    return new UserEventDetails(
                            event.getId(),
                            event.getName(),
                            event.getMarketMethod(),
                            event.getStatus(),
                            user.getMarketMakerEventIds().contains(event.getId()),
                            holdingDetails(event, position),
                            trades,
                            position == null ? 0.0 : position.getCommissionPaid(),
                            position == null ? 0.0 : position.getProfitLoss());
                })
                .toList();
        return new UserDetails(toUserSummary(user), events);
    }

    private List<HoldingDetails> holdingDetails(MarketEvent event, UserPosition position) {
        List<HoldingDetails> holdings = new ArrayList<>();
        for (int index = 0; index < event.getOptions().size(); index++) {
            holdings.add(new HoldingDetails(
                    index + 1,
                    event.getOptions().get(index).getName(),
                    position == null ? 0L : position.getHolding(index),
                    position == null ? 0.0 : position.getAmountPaid(index)));
        }
        return holdings;
    }

    private TradeDetails toTradeDetails(MarketEvent event, Trade trade) {
        return new TradeDetails(
                trade.sequenceNumber(),
                trade.kind(),
                event.getOptions().get(trade.optionIndex()).getName(),
                trade.quantity(),
                trade.pricePerShare(),
                trade.sharesCost(),
                trade.commission(),
                trade.totalPaid(),
                trade.buyerName(),
                trade.sellerName());
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
