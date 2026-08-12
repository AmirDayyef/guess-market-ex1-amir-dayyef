package com.guessmarket.console;

import com.guessmarket.api.GuessMarketEngine;
import com.guessmarket.api.dto.CommissionType;
import com.guessmarket.api.dto.EventDetails;
import com.guessmarket.api.dto.EventStatus;
import com.guessmarket.api.dto.EventSummary;
import com.guessmarket.api.dto.OptionDetails;
import com.guessmarket.api.dto.SettlementDetails;
import com.guessmarket.api.dto.TradeDetails;
import com.guessmarket.api.dto.TradeReceipt;
import com.guessmarket.api.exception.GuessMarketException;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

final class GuessMarketConsole {
    private static final String DIVIDER = "============================================================";
    private static final String SHORT_DIVIDER = "------------------------------------------------------------";

    private final GuessMarketEngine engine;
    private final Scanner scanner;
    private final PrintStream output;
    private boolean running = true;

    GuessMarketConsole(GuessMarketEngine engine, Scanner scanner, PrintStream output) {
        this.engine = engine;
        this.scanner = scanner;
        this.output = output;
    }

    void run() {
        printWelcome();
        while (running) {
            printMainMenu();
            Integer command = readInteger("Select a command: ");
            if (command == null) {
                output.println("\nInput ended. Goodbye.");
                return;
            }
            execute(command);
        }
    }

    private void execute(int command) {
        try {
            switch (command) {
                case 1 -> loadXml();
                case 2 -> displayAllEvents();
                case 3 -> displaySelectedEvent();
                case 4 -> participateInEvent();
                case 5 -> closeEvent();
                case 6 -> exit();
                case 7 -> saveState();
                case 8 -> loadState();
                default -> output.println("Invalid command. Please select a number from 1 to 8.");
            }
        } catch (GuessMarketException exception) {
            printError(exception.getMessage());
        } catch (RuntimeException exception) {
            printError("The operation could not be completed. Please check your input and try again.");
        }
    }

    private void loadXml() {
        String path = readLine("Enter the full path of the Exercise 1 XML file: ");
        if (path == null) {
            return;
        }
        engine.loadMarketFromXml(path);
        output.println("Market data loaded successfully. The previous market state was replaced.");
    }

    private void displayAllEvents() {
        printEventList(engine.getEvents(), "LOADED EVENTS");
    }

    private void displaySelectedEvent() {
        EventSummary selected = selectEvent(engine.getEvents(), "SELECT AN EVENT");
        if (selected != null) {
            printEventDetails(engine.getEventDetails(selected.id()));
        }
    }

    private void participateInEvent() {
        EventSummary selected = selectEvent(engine.getActiveEvents(), "SELECT AN ACTIVE EVENT");
        if (selected == null) {
            return;
        }

        EventDetails details = engine.getEventDetails(selected.id());
        printCurrentMarket(details);
        Integer optionNumber = readIntegerInRange(
                "Select an option number: ", 1, details.options().size());
        if (optionNumber == null) {
            return;
        }
        Long quantity = readPositiveLong("Enter the number of shares to buy: ");
        if (quantity == null) {
            return;
        }

        TradeReceipt receipt = engine.buyShares(selected.id(), optionNumber, quantity);
        output.println();
        output.println("PURCHASE COMPLETED");
        output.println(SHORT_DIVIDER);
        output.printf(Locale.US, "Option:          %s%n", receipt.optionName());
        output.printf(Locale.US, "Shares bought:   %,d%n", receipt.quantity());
        output.printf(Locale.US, "Shares cost:     %s%n", money(receipt.sharesCost()));
        output.printf(Locale.US, "Commission:      %s%n", money(receipt.commission()));
        output.printf(Locale.US, "Total paid:      %s%n", money(receipt.totalPaid()));
        printEventDetails(receipt.updatedEvent());
    }

    private void closeEvent() {
        EventSummary selected = selectEvent(engine.getActiveEvents(), "SELECT AN ACTIVE EVENT TO CLOSE");
        if (selected == null) {
            return;
        }

        EventDetails details = engine.getEventDetails(selected.id());
        printEventDetails(details);
        Integer winner = readIntegerInRange(
                "Select the winning option number: ", 1, details.options().size());
        if (winner == null) {
            return;
        }
        EventDetails closed = engine.closeEvent(selected.id(), winner);
        output.println("Event closed successfully.");
        printEventDetails(closed);
    }

    private void saveState() {
        String path = readLine("Enter the full path and file name for the saved state (without extension): ");
        if (path == null) {
            return;
        }
        engine.saveState(path);
        output.println("Market state saved successfully. The .gmstate extension was applied when needed.");
    }

    private void loadState() {
        String path = readLine("Enter the full path and file name of the saved state: ");
        if (path == null) {
            return;
        }
        engine.loadState(path);
        output.println("Saved market state loaded successfully.");
    }

    private void exit() {
        running = false;
        output.println("Thank you for using Guess Market. Goodbye.");
    }

    private EventSummary selectEvent(List<EventSummary> events, String title) {
        if (events.isEmpty()) {
            output.println("There are no events available for this operation.");
            return null;
        }
        printEventList(events, title);
        Integer selection = readIntegerInRange("Select an event number: ", 1, events.size());
        return selection == null ? null : events.get(selection - 1);
    }

    private void printEventList(List<EventSummary> events, String title) {
        output.println();
        output.println(DIVIDER);
        output.println(title);
        output.println(DIVIDER);
        for (int index = 0; index < events.size(); index++) {
            EventSummary event = events.get(index);
            output.printf(Locale.US, "%n[%d] Event ID: %d%n", index + 1, event.id());
            output.printf("Name:              %s%n", event.name());
            output.printf("Description:       %s%n", event.description());
            output.printf(Locale.US, "Commission:        %d%%%n", event.commissionPercentage());
            output.printf("Charging method:   %s%n", event.commissionType().displayName());
            output.println("Trading method:    LMSR");
            output.printf("Options:           %s / %s%n", event.optionNames().get(0), event.optionNames().get(1));
            output.printf("Status:            %s%n", event.status());
        }
        output.println(SHORT_DIVIDER);
    }

    private void printEventDetails(EventDetails details) {
        EventSummary event = details.summary();
        output.println();
        output.println(DIVIDER);
        output.printf(Locale.US, "EVENT DETAILS (ID: %d)%n", event.id());
        output.println(DIVIDER);
        output.printf("Name:              %s%n", event.name());
        output.printf("Description:       %s%n", event.description());
        output.printf("Status:            %s%n", event.status());
        output.printf(Locale.US, "Commission:        %d%% (%s)%n",
                event.commissionPercentage(), event.commissionType().displayName());
        output.printf(Locale.US, "Account balance:   %s%n", money(details.accountBalance()));
        output.printf(Locale.US, "Total commission:  %s%n", money(details.totalCommissionCollected()));

        printCurrentMarket(details);
        printTradeHistory(details.tradeHistory());
        if (event.status() == EventStatus.CLOSED) {
            printSettlement(details.settlement());
        }
        output.println(DIVIDER);
    }

    private void printCurrentMarket(EventDetails details) {
        output.println();
        output.println("CURRENT OPTION PRICING AND DISTRIBUTION");
        output.println(SHORT_DIVIDER);
        output.printf("%-4s %-24s %-18s %s%n", "No.", "Option", "Price (Probability)", "Shares Bought");
        output.println(SHORT_DIVIDER);
        for (OptionDetails option : details.options()) {
            String winner = option.winner() ? " [WINNER]" : "";
            output.printf(Locale.US, "%-4d %-24s $%-7.2f (%6.2f%%) %,d%s%n",
                    option.number(),
                    shorten(option.name(), 24),
                    option.currentPrice(),
                    option.currentPrice() * 100.0,
                    option.sharesBought(),
                    winner);
        }
    }

    private void printTradeHistory(List<TradeDetails> history) {
        output.println();
        output.println("TRADE HISTORY (NEWEST FIRST)");
        output.println(SHORT_DIVIDER);
        if (history.isEmpty()) {
            output.println("No trades have been completed for this event.");
            return;
        }
        output.printf("%-6s %-24s %-14s %s%n", "Trade", "Option", "Quantity", "Total Paid");
        output.println(SHORT_DIVIDER);
        for (TradeDetails trade : history) {
            output.printf(Locale.US, "#%-5d %-24s %,-14d %s%n",
                    trade.sequenceNumber(),
                    shorten(trade.optionName(), 24),
                    trade.quantity(),
                    money(trade.totalPaid()));
        }
    }

    private void printSettlement(SettlementDetails settlement) {
        output.println();
        output.println("FINAL SETTLEMENT");
        output.println(SHORT_DIVIDER);
        output.printf("Winning option:      %s%n", settlement.winningOptionName());
        output.printf(Locale.US, "Winning shares:      %,d%n", settlement.winningShares());
        output.printf(Locale.US, "Gross payout:        %s%n", money(settlement.grossPayout()));
        output.printf(Locale.US, "Closing commission:  %s%n", money(settlement.commission()));
        output.printf(Locale.US, "Payout after fee:    %s%n", money(settlement.payoutAfterCommission()));
        output.printf(Locale.US, "Final account:       %s%n", money(settlement.finalAccountBalance()));
    }

    private void printWelcome() {
        output.println(DIVIDER);
        output.println("                       GUESS MARKET");
        output.println("              Binary LMSR Prediction Market");
        output.println(DIVIDER);
    }

    private void printMainMenu() {
        output.println();
        output.println("MAIN MENU");
        output.println("1. Load market data from XML");
        output.println("2. Display all events");
        output.println("3. Display an event's trading status");
        output.println("4. Participate in an event (buy shares)");
        output.println("5. Close an event");
        output.println("6. Exit");
        output.println("7. Save the complete market state (bonus)");
        output.println("8. Load a saved market state (bonus)");
    }

    private Integer readInteger(String prompt) {
        while (true) {
            String line = readLine(prompt);
            if (line == null) {
                return null;
            }
            try {
                return Integer.valueOf(line.trim());
            } catch (NumberFormatException exception) {
                output.println("Invalid input. Please enter a whole number.");
            }
        }
    }

    private Integer readIntegerInRange(String prompt, int minimum, int maximum) {
        while (true) {
            Integer value = readInteger(prompt);
            if (value == null) {
                return null;
            }
            if (value >= minimum && value <= maximum) {
                return value;
            }
            output.printf(Locale.US, "Invalid selection. Enter a number from %d to %d.%n", minimum, maximum);
        }
    }

    private Long readPositiveLong(String prompt) {
        while (true) {
            String line = readLine(prompt);
            if (line == null) {
                return null;
            }
            try {
                long value = Long.parseLong(line.trim());
                if (value > 0) {
                    return value;
                }
                output.println("Invalid quantity. Enter a positive whole number.");
            } catch (NumberFormatException exception) {
                output.println("Invalid quantity. Enter a positive whole number within the supported range.");
            }
        }
    }

    private String readLine(String prompt) {
        output.print(prompt);
        if (!scanner.hasNextLine()) {
            return null;
        }
        return scanner.nextLine();
    }

    private void printError(String message) {
        output.println("ERROR: " + message);
    }

    private String money(double value) {
        if (Math.abs(value) < 0.0000001) {
            value = 0.0;
        }
        return String.format(Locale.US, value < 0 ? "-$%,.2f" : "$%,.2f", Math.abs(value));
    }

    private String shorten(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength - 3) + "...";
    }
}
