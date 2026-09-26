package com.guessmarket.server;

import com.guessmarket.api.dto.EventDetails;
import com.guessmarket.api.dto.AccountEntry;
import com.guessmarket.api.dto.MarketSnapshot;
import com.guessmarket.api.dto.OrderSide;
import com.guessmarket.api.dto.UserSummary;
import com.guessmarket.api.exception.InvalidOperationException;
import com.guessmarket.engine.GuessMarketEngineImpl;

import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All clients share this one in-memory market. Public methods serialize operations on it. */
public final class MarketSessionService {
    private static final long SESSION_TIMEOUT_MILLIS = Duration.ofSeconds(30).toMillis();
    private final GuessMarketEngineImpl engine = GuessMarketEngineImpl.emptyMarket();
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<String, List<AccountEntry>> accountEntries = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public synchronized LoginResult login(String requestedName) {
        String name = validateName(requestedName);
        final String requested = name;
        expireSessions();
        boolean connected = sessions.values().stream()
                .anyMatch(session -> session.userName.equalsIgnoreCase(requested));
        if (connected) {
            throw new InvalidOperationException("User " + name
                    + " is already connected. Choose another name.");
        }
        UserSummary existing = engine.getUsers().stream()
                .filter(user -> user.name().equalsIgnoreCase(requested))
                .findFirst().orElse(null);
        if (existing == null) {
            engine.registerUser(name);
            accountEntries.put(name.toLowerCase(Locale.ROOT), new ArrayList<>());
        } else {
            name = existing.name();
        }
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(token, new Session(name));
        return new LoginResult(token, name);
    }

    public synchronized void logout(String token) {
        sessions.remove(token);
    }

    public synchronized MarketSnapshot snapshot(String token) {
        String name = requireName(token);
        List<MarketSnapshot.PublicUser> users = engine.getUsers().stream()
                .map(user -> new MarketSnapshot.PublicUser(
                        user.name(), user.balance(), !user.marketMakerEventIds().isEmpty()))
                .toList();
        return new MarketSnapshot(engine.getEvents(), users,
                engine.getUserDetails(name), List.copyOf(entriesFor(name)));
    }

    public synchronized EventDetails eventDetails(String token, int eventId) {
        requireName(token);
        return engine.getEventDetails(eventId);
    }

    public synchronized String upload(String token, InputStream xml) {
        String name = requireName(token);
        int before = engine.getEvents().size();
        engine.addExercise3Events(xml, name);
        return "Added " + (engine.getEvents().size() - before) + " event(s). You are their market maker.";
    }

    public synchronized String deposit(String token, double amount) {
        String name = requireName(token);
        Map<String, Double> before = balances();
        engine.addFunds(name, amount);
        recordChanges(before, "Funds loaded by " + name);
        return "Added " + money(amount) + " to your account.";
    }

    public synchronized String open(String token, int eventId) {
        String name = requireName(token);
        String event = engine.getEventDetails(eventId).summary().name();
        Map<String, Double> before = balances();
        engine.openEvent(name, eventId);
        recordChanges(before, "Opened event " + event);
        return "Opened " + event + ".";
    }

    public synchronized String buy(String token, int eventId, int option, long quantity) {
        String name = requireName(token);
        String event = engine.getEventDetails(eventId).summary().name();
        Map<String, Double> before = balances();
        var receipt = engine.buyShares(name, eventId, option, quantity);
        recordChanges(before, "LMSR purchase in " + event);
        return "Bought " + quantity + " shares for " + money(receipt.totalPaid()) + ".";
    }

    public synchronized String order(
            String token, int eventId, int option, OrderSide side, long quantity, double price) {
        String name = requireName(token);
        String event = engine.getEventDetails(eventId).summary().name();
        Map<String, Double> before = balances();
        var receipt = engine.submitOrder(name, eventId, option, side, quantity, price);
        recordChanges(before, "Order Book trade in " + event);
        return receipt.message();
    }

    public synchronized String close(String token, int eventId, int winner) {
        String name = requireName(token);
        String event = engine.getEventDetails(eventId).summary().name();
        Map<String, Double> before = balances();
        engine.closeEvent(name, eventId, winner);
        recordChanges(before, "Settlement of " + event);
        return "Closed " + event + ".";
    }

    private Map<String, Double> balances() {
        Map<String, Double> values = new HashMap<>();
        for (UserSummary user : engine.getUsers()) {
            values.put(user.name().toLowerCase(Locale.ROOT), user.balance());
        }
        return values;
    }

    private void recordChanges(Map<String, Double> before, String description) {
        for (UserSummary user : engine.getUsers()) {
            String key = user.name().toLowerCase(Locale.ROOT);
            double change = user.balance() - before.getOrDefault(key, user.balance());
            if (Math.abs(change) > 0.0000001) {
                List<AccountEntry> entries = entriesFor(user.name());
                entries.add(new AccountEntry(entries.size() + 1L, description, change, user.balance()));
            }
        }
    }

    private List<AccountEntry> entriesFor(String name) {
        return accountEntries.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>());
    }

    private String requireName(String token) {
        Session session = token == null ? null : sessions.get(token);
        if (session == null || System.currentTimeMillis() - session.lastSeen > SESSION_TIMEOUT_MILLIS) {
            sessions.remove(token);
            throw new InvalidOperationException("Your session expired. Sign in again.");
        }
        session.lastSeen = System.currentTimeMillis();
        return session.userName;
    }

    private void expireSessions() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(session -> now - session.lastSeen > SESSION_TIMEOUT_MILLIS);
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidOperationException("Enter a user name.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 80 || trimmed.chars().anyMatch(character -> character < 32 || character > 126)) {
            throw new InvalidOperationException("Use at most 80 printable English characters for a user name.");
        }
        return trimmed;
    }

    private String money(double amount) {
        return String.format(Locale.US, "$%,.2f", amount);
    }

    public record LoginResult(String token, String userName) {
    }

    private static final class Session {
        private final String userName;
        private long lastSeen = System.currentTimeMillis();

        private Session(String userName) {
            this.userName = userName;
        }
    }
}
