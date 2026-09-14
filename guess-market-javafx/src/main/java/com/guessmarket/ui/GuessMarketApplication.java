package com.guessmarket.ui;

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
import com.guessmarket.api.dto.TradeReceipt;
import com.guessmarket.api.dto.UserDetails;
import com.guessmarket.api.dto.UserEventDetails;
import com.guessmarket.api.dto.UserSummary;
import com.guessmarket.engine.GuessMarketEngineImpl;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class GuessMarketApplication extends Application {
    private static final String ALL = "All";

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();
    private final TableView<EventSummary> eventTable = new TableView<>();
    private final TableView<UserSummary> userTable = new TableView<>();
    private final ComboBox<String> actingUserBox = new ComboBox<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> methodFilter = new ComboBox<>();
    private final ComboBox<String> commissionFilter = new ComboBox<>();
    private final VBox eventDetailsArea = new VBox(14);
    private final VBox userDetailsArea = new VBox(14);
    private final Label fileLabel = new Label("No XML file loaded");
    private final Label messageLabel = new Label("Load a market XML file to begin.");
    private final ProgressIndicator loadProgress = new ProgressIndicator();
    private final Button loadButton = new Button("Load XML");
    private final TabPane mainTabs = new TabPane();

    private Stage stage;
    private Integer selectedEventId;
    private String selectedUserName;
    private boolean refreshingUserTable;
    private boolean refreshingEventTable;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(buildHeader());
        root.setCenter(buildMainTabs());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1420, 880);
        scene.getStylesheets().add(getClass().getResource("/com/guessmarket/ui/guess-market.css").toExternalForm());

        primaryStage.setTitle("Guess Market");
        primaryStage.setMinWidth(820);
        primaryStage.setMinHeight(600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private Node buildHeader() {
        Label appTitle = new Label("GUESS MARKET");
        appTitle.getStyleClass().add("app-title");

        Label appSubtitle = new Label("prediction market dashboard");
        appSubtitle.getStyleClass().add("app-subtitle");

        VBox brand = new VBox(1, appTitle, appSubtitle);

        loadButton.getStyleClass().add("primary-button");
        loadButton.setOnAction(event -> chooseXmlFile());

        loadProgress.setPrefSize(22, 22);
        loadProgress.setVisible(false);
        loadProgress.setManaged(false);

        fileLabel.getStyleClass().add("file-label");
        fileLabel.setMaxWidth(Double.MAX_VALUE);

        Label actingAsLabel = new Label("Acting as");
        actingAsLabel.getStyleClass().add("muted-label");
        actingUserBox.setPromptText("Select a user");
        actingUserBox.setPrefWidth(180);
        actingUserBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && engine.hasLoadedMarket()) {
                selectedUserName = newValue;
                refreshUserTable();
                refreshEventTable();
            }
        });

        HBox topLine = new HBox(12, brand, flexibleSpacer(), actingAsLabel, actingUserBox);
        topLine.setAlignment(Pos.CENTER_LEFT);
        HBox fileLine = new HBox(10, loadProgress, loadButton, fileLabel);
        fileLine.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(fileLabel, Priority.ALWAYS);

        VBox header = new VBox(9, topLine, fileLine);
        header.getStyleClass().add("header");
        return header;
    }

    private Node buildMainTabs() {
        Tab eventsTab = new Tab("Events", buildEventsView());
        Tab usersTab = new Tab("Users", buildUsersView());
        eventsTab.setClosable(false);
        usersTab.setClosable(false);

        mainTabs.getTabs().setAll(eventsTab, usersTab);
        mainTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return mainTabs;
    }

    private Node buildStatusBar() {
        messageLabel.getStyleClass().add("status-label");
        HBox status = new HBox(messageLabel);
        status.getStyleClass().add("status-bar");
        return status;
    }

    private Node buildEventsView() {
        statusFilter.setItems(FXCollections.observableArrayList(
                ALL, "Inactive", "Active", "Closed"));
        methodFilter.setItems(FXCollections.observableArrayList(
                ALL, "LMSR", "Order Book"));
        commissionFilter.setItems(FXCollections.observableArrayList(
                ALL, "On purchase", "On close"));
        statusFilter.setValue(ALL);
        methodFilter.setValue(ALL);
        commissionFilter.setValue(ALL);
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> refreshEventTable());
        methodFilter.valueProperty().addListener((observable, oldValue, newValue) -> refreshEventTable());
        commissionFilter.valueProperty().addListener((observable, oldValue, newValue) -> refreshEventTable());

        GridPane filters = new GridPane();
        filters.setHgap(9);
        filters.setVgap(7);
        filters.addRow(0, new Label("Status"), statusFilter);
        filters.addRow(1, new Label("Method"), methodFilter);
        filters.addRow(2, new Label("Commission"), commissionFilter);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getStyleClass().add("filter-row");

        eventTable.setPlaceholder(new Label("Load an XML file to see events"));
        eventTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        eventTable.getColumns().add(stringColumn("ID", value -> Integer.toString(value.id()), 55));
        eventTable.getColumns().add(stringColumn("Event", EventSummary::name, 180));
        eventTable.getColumns().add(stringColumn("Method", value -> value.marketMethod().displayName(), 95));
        eventTable.getColumns().add(stringColumn("Status", value -> pretty(value.status()), 85));
        eventTable.getColumns().add(stringColumn("Market maker", EventSummary::marketMakerName, 120));
        eventTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null && !refreshingEventTable) {
                selectedEventId = selected.id();
                showEventDetails(selected.id());
            }
        });

        Label listTitle = sectionTitle("Market events");
        VBox left = new VBox(10, listTitle, filters, eventTable);
        VBox.setVgrow(eventTable, Priority.ALWAYS);
        left.getStyleClass().add("side-panel");
        left.setMinWidth(440);

        eventDetailsArea.setPadding(new Insets(20));
        eventDetailsArea.getChildren().setAll(emptyState(
                "No event selected",
                "Choose an event from the list to inspect prices, orders and participants."));
        ScrollPane detailsScroll = new ScrollPane(eventDetailsArea);
        detailsScroll.setFitToWidth(true);
        detailsScroll.getStyleClass().add("details-scroll");

        SplitPane split = new SplitPane(left, detailsScroll);
        split.setDividerPositions(0.36);
        return split;
    }

    private Node buildUsersView() {
        userTable.setPlaceholder(new Label("Load an XML file to see users"));
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        userTable.getColumns().add(stringColumn("User", UserSummary::name, 150));
        userTable.getColumns().add(moneyColumn("Balance", UserSummary::balance, 100));
        userTable.getColumns().add(stringColumn("State", value -> value.blocked() ? "Blocked" : "Active", 85));
        userTable.getColumns().add(stringColumn("MM events", value -> joinNumbers(value.marketMakerEventIds()), 85));
        userTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null && !refreshingUserTable) {
                selectedUserName = selected.name();
                actingUserBox.setValue(selected.name());
                showUserDetails(selected.name());
            }
        });

        VBox left = new VBox(10, sectionTitle("Market users"), userTable);
        VBox.setVgrow(userTable, Priority.ALWAYS);
        left.getStyleClass().add("side-panel");
        left.setMinWidth(390);

        userDetailsArea.setPadding(new Insets(20));
        userDetailsArea.getChildren().setAll(emptyState(
                "No user selected",
                "Choose a user to inspect their balance, holdings and activity."));
        ScrollPane detailsScroll = new ScrollPane(userDetailsArea);
        detailsScroll.setFitToWidth(true);
        detailsScroll.getStyleClass().add("details-scroll");

        SplitPane split = new SplitPane(left, detailsScroll);
        split.setDividerPositions(0.31);
        return split;
    }

    private void chooseXmlFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Guess Market XML");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            loadXmlInBackground(selected);
        }
    }

    private void loadXmlInBackground(File file) {
        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() {
                updateMessage("Validating and loading " + file.getName() + "...");
                try {
                    Thread.sleep(1_200L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("XML loading was interrupted.", exception);
                }
                engine.loadMarketFromXml(file.getAbsolutePath());
                return null;
            }
        };

        loadButton.setDisable(true);
        mainTabs.setDisable(true);
        actingUserBox.setDisable(true);
        loadProgress.setManaged(true);
        loadProgress.setVisible(true);
        messageLabel.textProperty().bind(loadTask.messageProperty());

        loadTask.setOnSucceeded(event -> {
            finishLoadTask();
            fileLabel.setText(file.getAbsolutePath());
            refreshAll();
            showMessage("Loaded " + engine.getEvents().size() + " events and "
                    + engine.getUsers().size() + " users from " + file.getName() + ".");
        });
        loadTask.setOnFailed(event -> {
            finishLoadTask();
            Throwable failure = loadTask.getException();
            showError("Could not load XML", failure == null ? "Unknown loading error" : failure.getMessage());
            showMessage(engine.hasLoadedMarket()
                    ? "The previous valid market is still loaded."
                    : "No market is currently loaded.");
        });

        Thread thread = new Thread(loadTask, "guess-market-xml-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void finishLoadTask() {
        messageLabel.textProperty().unbind();
        loadButton.setDisable(false);
        mainTabs.setDisable(false);
        actingUserBox.setDisable(false);
        loadProgress.setVisible(false);
        loadProgress.setManaged(false);
    }

    private void refreshAll() {
        String actor = actingUserBox.getValue();
        List<String> userNames = engine.getUsers().stream().map(UserSummary::name).toList();
        actingUserBox.setItems(FXCollections.observableArrayList(userNames));
        if (actor != null && userNames.contains(actor)) {
            actingUserBox.setValue(actor);
        } else if (!userNames.isEmpty()) {
            actingUserBox.setValue(userNames.getFirst());
        }

        refreshEventTable();
        refreshUserTable();
    }

    private void refreshEventTable() {
        if (!engine.hasLoadedMarket()) {
            return;
        }

        List<EventSummary> filtered = engine.getEvents().stream()
                .filter(this::passesEventFilters)
                .toList();
        EventSummary selection = filtered.stream()
                .filter(event -> selectedEventId != null && event.id() == selectedEventId)
                .findFirst()
                .orElse(filtered.isEmpty() ? null : filtered.getFirst());
        refreshingEventTable = true;
        try {
            eventTable.setItems(FXCollections.observableArrayList(filtered));
            if (selection == null) {
                eventTable.getSelectionModel().clearSelection();
            } else {
                eventTable.getSelectionModel().select(selection);
            }
        } finally {
            refreshingEventTable = false;
        }
        if (selection == null) {
            eventDetailsArea.getChildren().setAll(emptyState("No matching events", "Change the filters to see an event."));
        } else {
            selectedEventId = selection.id();
            showEventDetails(selection.id());
        }
    }

    private boolean passesEventFilters(EventSummary event) {
        String selectedStatus = statusFilter.getValue();
        String selectedMethod = methodFilter.getValue();
        String selectedCommission = commissionFilter.getValue();
        boolean statusMatches = ALL.equals(selectedStatus)
                || pretty(event.status()).equals(selectedStatus);
        boolean methodMatches = ALL.equals(selectedMethod)
                || event.marketMethod().displayName().equals(selectedMethod);
        boolean commissionMatches = ALL.equals(selectedCommission)
                || event.commissionType().displayName().equals(selectedCommission);
        return statusMatches && methodMatches && commissionMatches;
    }

    private void refreshUserTable() {
        if (!engine.hasLoadedMarket()) {
            return;
        }
        List<UserSummary> users = engine.getUsers();
        UserSummary selection = users.stream()
                .filter(user -> user.name().equals(selectedUserName))
                .findFirst()
                .orElse(users.isEmpty() ? null : users.getFirst());
        refreshingUserTable = true;
        try {
            userTable.setItems(FXCollections.observableArrayList(users));
            if (selection != null) {
                userTable.getSelectionModel().select(selection);
            }
        } finally {
            refreshingUserTable = false;
        }
        if (selection != null) {
            selectedUserName = selection.name();
            showUserDetails(selection.name());
        }
    }

    private void showEventDetails(int eventId) {
        EventDetails event = engine.getEventDetails(eventId);
        eventDetailsArea.getChildren().setAll(buildEventDetailsContent(event));
    }

    private Node buildEventDetailsContent(EventDetails event) {
        EventSummary summary = event.summary();
        VBox content = new VBox(14);

        Label title = new Label(summary.name());
        title.getStyleClass().add("details-title");
        Label description = new Label(summary.description());
        description.setWrapText(true);
        description.getStyleClass().add("description");

        Label status = badge(pretty(summary.status()), summary.status().name().toLowerCase(Locale.ROOT));
        Label method = badge(summary.marketMethod().displayName(), "method");
        HBox badges = new HBox(8, status, method);

        GridPane facts = detailsGrid();
        addFact(facts, 0, "Event ID", Integer.toString(summary.id()));
        addFact(facts, 1, "Market maker", summary.marketMakerName());
        addFact(facts, 2, "Commission", summary.commissionPercentage() + "% · " + summary.commissionType().displayName());
        addFact(facts, 3, "Event account", money(summary.accountBalance()));
        if (summary.marketMethod() == MarketMethod.LMSR) {
            addFact(facts, 4, "Liquidity", Integer.toString(event.liquidity()));
            addFact(facts, 5, "Collected fees", money(event.totalCommissionCollected()));
        } else {
            addFact(facts, 4, "Initial / base", money(event.initialInvestment()) + " / " + money(event.baseValue()));
            addFact(facts, 5, "Peer mint", Boolean.TRUE.equals(event.mintAllowed()) ? "Allowed" : "Disabled");
        }

        VBox heading = card(new VBox(8, title, badges, description, facts));
        content.getChildren().addAll(heading, buildEventActions(event),
                titled("Options", buildOptionsTable(event.options())));

        if (summary.marketMethod() == MarketMethod.ORDER_BOOK) {
            content.getChildren().add(titled("Open order books", buildOrderBooks(event)));
        }
        content.getChildren().addAll(
                titled("Participants", buildParticipantsTable(event.participants())),
                titled("Trade history", buildTradesTable(event.tradeHistory())));

        if (event.settlement() != null) {
            content.getChildren().add(titled("Settlement", buildSettlement(event.settlement())));
        }
        return content;
    }

    private Node buildEventActions(EventDetails event) {
        EventSummary summary = event.summary();
        String actor = actingUserBox.getValue();
        boolean isMarketMaker = actor != null && actor.equals(summary.marketMakerName());
        boolean actorBlocked = actor != null && engine.getUserDetails(actor).summary().blocked();

        Label title = sectionTitle("Actions");
        Label context = new Label(actor == null
                ? "Choose an acting user in the top bar."
                : "Orders and lifecycle actions will be submitted as " + actor + ".");
        context.getStyleClass().add("muted-label");

        FlowPane lifecycle = new FlowPane(10, 8);
        lifecycle.setAlignment(Pos.CENTER_LEFT);
        Button open = new Button("Open event");
        open.getStyleClass().add("primary-button");
        open.setDisable(summary.status() != EventStatus.INACTIVE || !isMarketMaker || actorBlocked);
        open.setOnAction(action -> runAction(() -> {
            engine.openEvent(actor, summary.id());
            return "Event opened by " + actor + ".";
        }));
        lifecycle.getChildren().add(open);

        ComboBox<OptionDetails> winnerBox = optionBox(event.options());
        winnerBox.setPromptText("Winning option");
        Button close = new Button("Close event");
        close.getStyleClass().add("danger-button");
        close.setDisable(summary.status() != EventStatus.ACTIVE || !isMarketMaker || actorBlocked);
        close.setOnAction(action -> {
            OptionDetails winner = winnerBox.getValue();
            if (winner == null) {
                showError("Missing winning option", "Choose the winning option before closing the event.");
                return;
            }
            runAction(() -> {
                engine.closeEvent(actor, summary.id(), winner.number());
                return "Event closed. Winner: " + winner.name() + ".";
            });
        });
        lifecycle.getChildren().addAll(winnerBox, close);

        Node trading = summary.marketMethod() == MarketMethod.LMSR
                ? buildLmsrTradeForm(event, actor, actorBlocked)
                : buildOrderForm(event, actor, actorBlocked);

        Label warning = new Label();
        warning.getStyleClass().add("warning-label");
        if (actorBlocked) {
            warning.setText(actor + " has a negative balance and cannot perform another action.");
        } else if (actor != null && !isMarketMaker && summary.status() == EventStatus.INACTIVE) {
            warning.setText("Only " + summary.marketMakerName() + " can open this event.");
        }
        warning.setVisible(!warning.getText().isBlank());
        warning.setManaged(warning.isVisible());

        return card(new VBox(12, title, context, lifecycle, new Separator(), trading, warning));
    }

    private Node buildLmsrTradeForm(EventDetails event, String actor, boolean actorBlocked) {
        ComboBox<OptionDetails> optionBox = optionBox(event.options());
        optionBox.setPromptText("Option");
        TextField quantity = numericField("Quantity");
        Button buy = new Button("Buy shares");
        buy.getStyleClass().add("accent-button");
        buy.setDisable(event.summary().status() != EventStatus.ACTIVE || actor == null || actorBlocked);
        buy.setOnAction(action -> runAction(() -> {
            OptionDetails option = requireOption(optionBox);
            long amount = positiveLong(quantity, "Quantity");
            TradeReceipt receipt = engine.buyShares(actor, event.summary().id(), option.number(), amount);
            return "Bought " + amount + " shares of " + option.name() + " for " + money(receipt.totalPaid()) + ".";
        }));

        FlowPane form = new FlowPane(10, 8, new Label("LMSR purchase"), optionBox, quantity, buy);
        form.setAlignment(Pos.CENTER_LEFT);
        return form;
    }

    private Node buildOrderForm(EventDetails event, String actor, boolean actorBlocked) {
        ComboBox<OptionDetails> optionBox = optionBox(event.options());
        optionBox.setPromptText("Option");
        ComboBox<OrderSide> side = new ComboBox<>(FXCollections.observableArrayList(OrderSide.values()));
        side.setPromptText("Buy / sell");
        TextField quantity = numericField("Quantity");
        TextField price = numericField("Price");
        Button submit = new Button("Submit order");
        submit.getStyleClass().add("accent-button");
        submit.setDisable(event.summary().status() != EventStatus.ACTIVE || actor == null || actorBlocked);
        submit.setOnAction(action -> runAction(() -> {
            OptionDetails option = requireOption(optionBox);
            if (side.getValue() == null) {
                throw new IllegalArgumentException("Choose BUY or SELL.");
            }
            long amount = positiveLong(quantity, "Quantity");
            double orderPrice = positiveDouble(price, "Price");
            OrderReceipt receipt = engine.submitOrder(actor, event.summary().id(), option.number(),
                    side.getValue(), amount, orderPrice);
            return receipt.message();
        }));

        FlowPane form = new FlowPane(10, 8, new Label("Order book"), optionBox, side, quantity, price, submit);
        form.setAlignment(Pos.CENTER_LEFT);
        return form;
    }

    private TableView<OptionDetails> buildOptionsTable(List<OptionDetails> options) {
        TableView<OptionDetails> table = compactTable(options, 190);
        table.getColumns().add(numberColumn("#", OptionDetails::number, 42));
        table.getColumns().add(stringColumn("Option", OptionDetails::name, 150));
        table.getColumns().add(nullableMoneyColumn("Price", OptionDetails::currentPrice, 85));
        table.getColumns().add(longColumn("Issued", OptionDetails::sharesIssued, 80));
        table.getColumns().add(nullableMoneyColumn("Best bid", OptionDetails::bestBid, 85));
        table.getColumns().add(nullableMoneyColumn("Best ask", OptionDetails::bestAsk, 85));
        table.getColumns().add(nullableMoneyColumn("Spread", OptionDetails::spread, 85));
        table.getColumns().add(stringColumn("Result", option -> option.winner() ? "Winner" : "", 70));
        return table;
    }

    private TableView<OrderDetails> buildOrdersTable(List<OrderDetails> orders) {
        TableView<OrderDetails> table = compactTable(orders, 210);
        table.setPlaceholder(new Label("No open orders"));
        table.getColumns().add(longColumn("Seq", OrderDetails::sequenceNumber, 55));
        table.getColumns().add(stringColumn("User", OrderDetails::userName, 110));
        table.getColumns().add(stringColumn("Option", OrderDetails::optionName, 130));
        table.getColumns().add(stringColumn("Side", value -> value.side().name(), 65));
        table.getColumns().add(longColumn("Quantity", OrderDetails::quantity, 80));
        table.getColumns().add(moneyColumn("Price", OrderDetails::price, 80));
        return table;
    }

    private Node buildOrderBooks(EventDetails event) {
        HBox books = new HBox(12);
        for (OptionDetails option : event.options()) {
            List<OrderDetails> optionOrders = event.orderBook().stream()
                    .filter(order -> order.optionNumber() == option.number())
                    .toList();
            TableView<OrderDetails> table = buildOrdersTable(optionOrders);
            FlowPane prices = new FlowPane(12, 6,
                    new Label("LAST " + optionalMoney(option.lastTradePrice())),
                    new Label("BID " + optionalMoney(option.bestBid())),
                    new Label("ASK " + optionalMoney(option.bestAsk())),
                    new Label("MID " + optionalMoney(option.midPrice())),
                    new Label("SPREAD " + optionalMoney(option.spread())));
            VBox book = new VBox(8, sectionTitle(option.name()), prices, table);
            book.getStyleClass().add("order-book");
            book.setMinWidth(300);
            HBox.setHgrow(book, Priority.ALWAYS);
            books.getChildren().add(book);
        }
        return books;
    }

    private TableView<ParticipantDetails> buildParticipantsTable(List<ParticipantDetails> participants) {
        TableView<ParticipantDetails> table = compactTable(participants, 210);
        table.setPlaceholder(new Label("No participants yet"));
        table.getColumns().add(stringColumn("User", ParticipantDetails::userName, 115));
        table.getColumns().add(stringColumn("Holdings / paid", value -> holdingsText(value.holdings()), 260));
        table.getColumns().add(moneyColumn("Commission", ParticipantDetails::commissionPaid, 100));
        table.getColumns().add(moneyColumn("P / L", ParticipantDetails::profitLoss, 100));
        table.getColumns().add(stringColumn("Open orders", value -> value.hasOpenOrders() ? "Yes" : "No", 90));
        return table;
    }

    private TableView<TradeDetails> buildTradesTable(List<TradeDetails> trades) {
        TableView<TradeDetails> table = compactTable(trades, 235);
        table.setPlaceholder(new Label("No completed trades"));
        table.getColumns().add(longColumn("Seq", TradeDetails::sequenceNumber, 52));
        table.getColumns().add(stringColumn("Kind", value -> pretty(value.kind()), 105));
        table.getColumns().add(stringColumn("Option", TradeDetails::optionName, 120));
        table.getColumns().add(longColumn("Qty", TradeDetails::quantity, 65));
        table.getColumns().add(moneyColumn("Price", TradeDetails::pricePerShare, 80));
        table.getColumns().add(moneyColumn("Fee", TradeDetails::commission, 75));
        table.getColumns().add(stringColumn("Buyer", value -> dash(value.buyerName()), 100));
        table.getColumns().add(stringColumn("Seller", value -> dash(value.sellerName()), 100));
        return table;
    }

    private Node buildSettlement(SettlementDetails settlement) {
        GridPane grid = detailsGrid();
        addFact(grid, 0, "Winning option", settlement.winningOptionName());
        addFact(grid, 1, "Winning shares", Long.toString(settlement.winningShares()));
        addFact(grid, 2, "Gross payout", money(settlement.grossPayout()));
        addFact(grid, 3, "Closing commission", money(settlement.commission()));
        addFact(grid, 4, "Paid to winners", money(settlement.payoutAfterCommission()));
        addFact(grid, 5, "Returned to MM", money(settlement.returnedToMarketMaker()));
        return grid;
    }

    private void showUserDetails(String userName) {
        UserDetails user = engine.getUserDetails(userName);
        UserSummary summary = user.summary();
        userDetailsArea.getChildren().clear();

        Label title = new Label(summary.name());
        title.getStyleClass().add("details-title");
        Label balance = new Label(money(summary.balance()));
        balance.getStyleClass().add("balance-value");
        Label state = badge(summary.blocked() ? "Blocked" : "Active", summary.blocked() ? "closed" : "active");

        GridPane facts = detailsGrid();
        addFact(facts, 0, "Current balance", money(summary.balance()));
        addFact(facts, 1, "Market maker events", joinNumbers(summary.marketMakerEventIds()));
        addFact(facts, 2, "Participating events", joinNumbers(summary.participatingEventIds()));

        Button actAs = new Button("Act as " + summary.name());
        actAs.getStyleClass().add("primary-button");
        actAs.setOnAction(event -> {
            actingUserBox.setValue(summary.name());
            showMessage("Actions will now be submitted as " + summary.name() + ".");
        });
        HBox userHeader = new HBox(12, title, balance, state, flexibleSpacer(), actAs);
        userHeader.setAlignment(Pos.CENTER_LEFT);
        userDetailsArea.getChildren().add(card(new VBox(12, userHeader, facts)));

        TableView<UserEventDetails> events = compactTable(user.events(), 240);
        events.setPlaceholder(new Label("This user has no market activity yet"));
        events.getColumns().add(numberColumn("ID", UserEventDetails::eventId, 50));
        events.getColumns().add(stringColumn("Event", UserEventDetails::eventName, 155));
        events.getColumns().add(stringColumn("Role", value -> value.marketMaker() ? "Market maker" : "Participant", 105));
        events.getColumns().add(stringColumn("Status", value -> pretty(value.status()), 80));
        events.getColumns().add(stringColumn("Holdings / paid", value -> holdingsText(value.holdings()), 240));
        events.getColumns().add(moneyColumn("Commission", UserEventDetails::commissionPaid, 95));
        events.getColumns().add(moneyColumn("P / L", UserEventDetails::profitLoss, 95));
        userDetailsArea.getChildren().add(titled("Event ownership and participation", events));

        TableView<EventSummary> activeEvents = compactTable(engine.getActiveEvents(), 190);
        activeEvents.setPlaceholder(new Label("There are no active events"));
        activeEvents.getColumns().add(stringColumn("ID", value -> Integer.toString(value.id()), 50));
        activeEvents.getColumns().add(stringColumn("Event", EventSummary::name, 180));
        activeEvents.getColumns().add(stringColumn("Method", value -> value.marketMethod().displayName(), 95));
        activeEvents.getColumns().add(stringColumn("Market maker", EventSummary::marketMakerName, 115));
        userDetailsArea.getChildren().add(titled("Active markets available to this user", activeEvents));

        VBox selectedEventArea = new VBox(14);
        selectedEventArea.getChildren().add(emptyState(
                "No event selected",
                "Choose a related event or an active market above to inspect it and trade as this user."));
        events.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, activity) -> {
            if (activity != null) {
                activeEvents.getSelectionModel().clearSelection();
                selectedEventId = activity.eventId();
                selectedEventArea.getChildren().setAll(
                        buildEventDetailsContent(engine.getEventDetails(activity.eventId())));
            }
        });
        activeEvents.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, event) -> {
            if (event != null) {
                events.getSelectionModel().clearSelection();
                selectedEventId = event.id();
                selectedEventArea.getChildren().setAll(buildEventDetailsContent(engine.getEventDetails(event.id())));
            }
        });
        userDetailsArea.getChildren().add(titled("Selected event details and trade", selectedEventArea));

        UserEventDetails selectedActivity = user.events().stream()
                .filter(activity -> selectedEventId != null && activity.eventId() == selectedEventId)
                .findFirst()
                .orElse(null);
        if (selectedActivity != null) {
            events.getSelectionModel().select(selectedActivity);
        } else {
            EventSummary selectedActiveEvent = engine.getActiveEvents().stream()
                    .filter(event -> selectedEventId != null && event.id() == selectedEventId)
                    .findFirst()
                    .orElse(engine.getActiveEvents().isEmpty() ? null : engine.getActiveEvents().getFirst());
            if (selectedActiveEvent != null) {
                activeEvents.getSelectionModel().select(selectedActiveEvent);
            } else if (!user.events().isEmpty()) {
                events.getSelectionModel().select(user.events().getFirst());
            }
        }
    }

    private void runAction(Action action) {
        try {
            String actor = actingUserBox.getValue();
            String result = action.run();
            refreshAll();
            showMessage(result);
            if (actor != null && engine.getUserDetails(actor).summary().blocked()) {
                showError("Negative balance warning",
                        actor + " completed the action, but their balance is now negative. Future actions are blocked.");
            }
        } catch (RuntimeException exception) {
            showError("Action rejected", exception.getMessage());
        }
    }

    private ComboBox<OptionDetails> optionBox(List<OptionDetails> options) {
        ComboBox<OptionDetails> box = new ComboBox<>(FXCollections.observableArrayList(options));
        box.setPrefWidth(165);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(OptionDetails option) {
                return option == null ? "" : option.name();
            }

            @Override
            public OptionDetails fromString(String value) {
                return options.stream()
                        .filter(option -> option.name().equalsIgnoreCase(value))
                        .findFirst()
                        .orElse(null);
            }
        });
        return box;
    }

    private OptionDetails requireOption(ComboBox<OptionDetails> box) {
        if (box.getValue() == null) {
            throw new IllegalArgumentException("Choose an option.");
        }
        return box.getValue();
    }

    private TextField numericField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefColumnCount(8);
        return field;
    }

    private String optionalMoney(Double amount) {
        return amount == null ? "-" : money(amount);
    }

    private long positiveLong(TextField field, String name) {
        try {
            long value = Long.parseLong(field.getText().trim());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive whole number.");
        }
    }

    private double positiveDouble(TextField field, String name) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (!Double.isFinite(value) || value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive number.");
        }
    }

    private GridPane detailsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(10);
        for (int index = 0; index < 3; index++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(33.33);
            grid.getColumnConstraints().add(constraints);
        }
        return grid;
    }

    private void addFact(GridPane grid, int position, String name, String value) {
        Label key = new Label(name.toUpperCase(Locale.ROOT));
        key.getStyleClass().add("fact-key");
        Label content = new Label(dash(value));
        content.getStyleClass().add("fact-value");
        content.setWrapText(true);
        VBox cell = new VBox(2, key, content);
        grid.add(cell, position % 3, position / 3);
    }

    private VBox card(Node content) {
        VBox card = new VBox(content);
        card.getStyleClass().add("card");
        return card;
    }

    private TitledPane titled(String title, Node content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(true);
        pane.setCollapsible(true);
        pane.getStyleClass().add("data-pane");
        return pane;
    }

    private Node emptyState(String title, String text) {
        Label heading = new Label(title);
        heading.getStyleClass().add("empty-title");
        Label description = new Label(text);
        description.setWrapText(true);
        description.getStyleClass().add("muted-label");
        VBox empty = new VBox(8, heading, description);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(90, 30, 30, 30));
        return empty;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Label badge(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().addAll("badge", "badge-" + style);
        return label;
    }

    private Region flexibleSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private <T> TableView<T> compactTable(List<T> values, double height) {
        TableView<T> table = new TableView<>(FXCollections.observableArrayList(values));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(height);
        table.setMinHeight(130);
        return table;
    }

    private <T> TableColumn<T, String> stringColumn(String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(dash(value.apply(cell.getValue()))));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty || item == null ? null : new Tooltip(item));
            }
        });
        column.setPrefWidth(width);
        return column;
    }

    private <T> TableColumn<T, Number> numberColumn(String title, Function<T, Integer> value, double width) {
        TableColumn<T, Number> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private <T> TableColumn<T, Number> longColumn(String title, Function<T, Long> value, double width) {
        TableColumn<T, Number> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyLongWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private <T> TableColumn<T, Number> moneyColumn(String title, Function<T, Double> value, double width) {
        TableColumn<T, Number> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyDoubleWrapper(value.apply(cell.getValue())));
        column.setCellFactory(ignored -> new MoneyTableCell<>());
        column.setPrefWidth(width);
        return column;
    }

    private <T> TableColumn<T, String> nullableMoneyColumn(String title, Function<T, Double> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> {
            Double number = value.apply(cell.getValue());
            return new ReadOnlyStringWrapper(number == null ? "—" : money(number));
        });
        column.setPrefWidth(width);
        return column;
    }

    private String holdingsText(List<HoldingDetails> holdings) {
        if (holdings.isEmpty()) {
            return "—";
        }
        return holdings.stream()
                .filter(holding -> holding.shares() != 0)
                .map(holding -> holding.optionName() + ": " + holding.shares()
                        + " (" + money(holding.amountPaid()) + ")")
                .reduce((left, right) -> left + " · " + right)
                .orElse("—");
    }

    private String joinNumbers(List<Integer> numbers) {
        return numbers.isEmpty() ? "—" : numbers.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + ", " + right)
                .orElse("—");
    }

    private static String money(double value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    private static String pretty(Enum<?> value) {
        String lower = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private void showMessage(String message) {
        messageLabel.setText(message);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Guess Market");
        alert.setHeaderText(title);
        alert.setContentText(dash(message));
        alert.showAndWait();
    }

    @Override
    public void stop() {
        Platform.exit();
    }

    @FunctionalInterface
    private interface Action {
        String run();
    }
}
