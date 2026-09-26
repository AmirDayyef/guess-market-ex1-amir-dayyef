package com.guessmarket.client;

import com.guessmarket.api.dto.AccountEntry;
import com.guessmarket.api.dto.EventDetails;
import com.guessmarket.api.dto.EventStatus;
import com.guessmarket.api.dto.EventSummary;
import com.guessmarket.api.dto.HoldingDetails;
import com.guessmarket.api.dto.MarketMethod;
import com.guessmarket.api.dto.MarketSnapshot;
import com.guessmarket.api.dto.OptionDetails;
import com.guessmarket.api.dto.OrderDetails;
import com.guessmarket.api.dto.OrderSide;
import com.guessmarket.api.dto.ParticipantDetails;
import com.guessmarket.api.dto.TradeDetails;
import com.guessmarket.api.dto.UserEventDetails;
import javafx.application.Application;
import javafx.application.Platform;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class GuessMarketClientApplication extends Application {
    private final HttpMarketClient client = new HttpMarketClient();
    private final ScheduledExecutorService puller = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "guess-market-pull");
        thread.setDaemon(true);
        return thread;
    });
    private final TableView<EventSummary> events = new TableView<>();
    private final TableView<MarketSnapshot.PublicUser> users = new TableView<>();
    private final TableView<UserEventDetails> myEvents = new TableView<>();
    private final TableView<AccountEntry> accountEntries = new TableView<>();
    private final VBox eventContent = new VBox(12);
    private final Label balanceLabel = new Label("$0.00");
    private final Label statusLabel = new Label("Ready");
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> methodFilter = new ComboBox<>();
    private final ComboBox<String> commissionFilter = new ComboBox<>();
    private Stage stage;
    private TabPane tabs;
    private MarketSnapshot snapshot;
    private EventDetails currentEvent;
    private volatile Integer selectedEventId;
    private String userName;
    private boolean refreshing;
    private boolean polling;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("Guess Market - Client");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.setScene(loginScene());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private Scene loginScene() {
        Label title = new Label("GUESS MARKET");
        title.getStyleClass().add("brand");
        Label hint = new Label("Sign in with a unique name to join the shared market.");
        TextField name = new TextField();
        name.setPromptText("Your name");
        name.setMaxWidth(320);
        Button signIn = new Button("Sign in");
        signIn.getStyleClass().add("primary");
        Label feedback = new Label("Start the Guess Market server in Tomcat first.");
        feedback.setWrapText(true);
        signIn.setOnAction(event -> {
            String requestedName = name.getText();
            signIn.setDisable(true);
            feedback.setText("Connecting...");
            runAsync(() -> client.login(requestedName), connectedName -> {
                userName = connectedName;
                stage.setScene(mainScene());
                startPulling();
            }, error -> {
                signIn.setDisable(false);
                feedback.setText(error.getMessage());
            });
        });
        name.setOnAction(event -> signIn.fire());
        VBox box = new VBox(16, title, hint, name, signIn, feedback);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        Scene scene = new Scene(box, 1000, 720);
        style(scene);
        return scene;
    }

    private Scene mainScene() {
        Label brand = new Label("GUESS MARKET");
        brand.getStyleClass().add("brand");
        Label identity = new Label("Signed in as " + userName);
        Button upload = new Button("Upload XML");
        upload.getStyleClass().add("primary");
        upload.setOnAction(event -> chooseUpload(upload));
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> pullOnce());
        HBox header = new HBox(12, brand, spacer(), identity, upload, refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");

        tabs = new TabPane();
        Tab marketTab = new Tab("Events", eventsView());
        Tab accountTab = new Tab("Account", accountView());
        marketTab.setClosable(false);
        accountTab.setClosable(false);
        tabs.getTabs().setAll(marketTab, accountTab);

        statusLabel.getStyleClass().add("status");
        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(tabs);
        root.setBottom(statusLabel);
        Scene scene = new Scene(root, 1420, 880);
        style(scene);
        return scene;
    }

    private Node eventsView() {
        statusFilter.setItems(FXCollections.observableArrayList("All", "Inactive", "Active", "Closed"));
        methodFilter.setItems(FXCollections.observableArrayList("All", "LMSR", "Order Book"));
        commissionFilter.setItems(FXCollections.observableArrayList("All", "On purchase", "On close"));
        statusFilter.setValue("All");
        methodFilter.setValue("All");
        commissionFilter.setValue("All");
        statusFilter.valueProperty().addListener((value, old, next) -> updateEventTable());
        methodFilter.valueProperty().addListener((value, old, next) -> updateEventTable());
        commissionFilter.valueProperty().addListener((value, old, next) -> updateEventTable());
        FlowPane filters = new FlowPane(8, 8,
                new Label("Status"), statusFilter,
                new Label("Method"), methodFilter,
                new Label("Commission"), commissionFilter);

        setupTable(events);
        events.setPlaceholder(new Label("No events yet. Upload an Exercise 3 XML file."));
        events.getColumns().add(column("Event", EventSummary::name, 190));
        events.getColumns().add(column("Method", event -> event.marketMethod().displayName(), 95));
        events.getColumns().add(column("Status", event -> pretty(event.status()), 80));
        events.getColumns().add(column("Market maker", EventSummary::marketMakerName, 120));
        events.getSelectionModel().selectedItemProperty().addListener((value, old, selected) -> {
            if (!refreshing && selected != null) {
                selectedEventId = selected.id();
                currentEvent = null;
                pullOnce();
            }
        });
        VBox eventList = new VBox(10, new Label("Market events"), filters, events);
        eventList.getStyleClass().add("panel");
        eventList.setMinWidth(420);
        VBox.setVgrow(events, Priority.ALWAYS);

        eventContent.setPadding(new Insets(18));
        eventContent.getChildren().setAll(new Label("Select an event to see prices and trade."));
        ScrollPane details = new ScrollPane(eventContent);
        details.setFitToWidth(true);
        SplitPane split = new SplitPane(eventList, details);
        split.setDividerPositions(0.36);
        return split;
    }

    private Node accountView() {
        balanceLabel.getStyleClass().add("balance");
        TextField funds = new TextField();
        funds.setPromptText("Amount to add");
        funds.setPrefColumnCount(9);
        Button deposit = new Button("Load funds");
        deposit.getStyleClass().add("primary");
        deposit.setOnAction(event -> {
            try {
                double amount = positiveDouble(funds, "Amount");
                execute(() -> client.deposit(amount));
            } catch (IllegalArgumentException error) {
                showError(error.getMessage());
            }
        });
        FlowPane balance = new FlowPane(12, 10,
                new Label("Your balance"), balanceLabel, funds, deposit);
        balance.getStyleClass().add("panel");

        setupTable(users);
        users.setPrefHeight(170);
        users.getColumns().add(column("User", MarketSnapshot.PublicUser::name, 155));
        users.getColumns().add(column("Balance", item -> money(item.balance()), 100));
        users.getColumns().add(column("Market maker", item -> item.marketMaker() ? "Yes" : "No", 100));

        setupTable(myEvents);
        myEvents.setPrefHeight(190);
        myEvents.getColumns().add(column("Event", UserEventDetails::eventName, 180));
        myEvents.getColumns().add(column("Role", item -> item.marketMaker() ? "Market maker" : "Participant", 120));
        myEvents.getColumns().add(column("Status", item -> pretty(item.status()), 80));
        myEvents.getColumns().add(column("Holdings", item -> holdings(item.holdings()), 260));
        myEvents.getSelectionModel().selectedItemProperty().addListener((value, old, selected) -> {
            if (selected != null) {
                selectedEventId = selected.eventId();
                tabs.getSelectionModel().select(0);
                updateEventTable();
                pullOnce();
            }
        });

        setupTable(accountEntries);
        accountEntries.setPrefHeight(230);
        accountEntries.getColumns().add(column("#", item -> Long.toString(item.sequence()), 50));
        accountEntries.getColumns().add(column("Activity", AccountEntry::description, 320));
        accountEntries.getColumns().add(column("Change", item -> signedMoney(item.change()), 100));
        accountEntries.getColumns().add(column("Balance", item -> money(item.balance()), 100));

        VBox content = new VBox(14,
                balance,
                section("Market users", users),
                section("Your events and holdings", myEvents),
                section("Account history", accountEntries));
        content.setPadding(new Insets(14));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private void startPulling() {
        puller.scheduleWithFixedDelay(() -> {
            if (!polling) {
                polling = true;
                try {
                    MarketSnapshot latest = client.snapshot();
                    Integer id = selectedEventId;
                    EventDetails details = id == null ? null : client.event(id);
                    Platform.runLater(() -> render(latest, details));
                } catch (RuntimeException error) {
                    Platform.runLater(() -> statusLabel.setText(error.getMessage()));
                } finally {
                    polling = false;
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void pullOnce() {
        runAsync(() -> new PullResult(client.snapshot(),
                        selectedEventId == null ? null : client.event(selectedEventId)),
                result -> render(result.market(), result.event()),
                error -> statusLabel.setText(error.getMessage()));
    }

    private void render(MarketSnapshot latest, EventDetails details) {
        if (stage.getScene() == null || tabs == null) return;
        boolean eventsChanged = snapshot == null || !snapshot.events().equals(latest.events());
        snapshot = latest;
        balanceLabel.setText(money(latest.account().summary().balance()));
        users.setItems(FXCollections.observableArrayList(latest.users()));
        myEvents.setItems(FXCollections.observableArrayList(latest.account().events()));
        accountEntries.setItems(FXCollections.observableArrayList(latest.accountEntries()));
        if (eventsChanged) updateEventTable();
        if (details != null && selectedEventId != null && details.summary().id() == selectedEventId
                && (!details.equals(currentEvent) || eventContent.getChildren().isEmpty())
                && !editingInput()) {
            currentEvent = details;
            eventContent.getChildren().setAll(eventDetails(details));
        }
    }

    private boolean editingInput() {
        Node focused = stage.getScene().getFocusOwner();
        return focused instanceof TextInputControl || focused instanceof ComboBox<?>;
    }

    private void updateEventTable() {
        if (snapshot == null) return;
        List<EventSummary> filtered = snapshot.events().stream().filter(event ->
                ("All".equals(statusFilter.getValue()) || pretty(event.status()).equals(statusFilter.getValue()))
                        && ("All".equals(methodFilter.getValue())
                        || event.marketMethod().displayName().equals(methodFilter.getValue()))
                        && ("All".equals(commissionFilter.getValue())
                        || event.commissionType().displayName().equals(commissionFilter.getValue()))).toList();
        refreshing = true;
        try {
            events.setItems(FXCollections.observableArrayList(filtered));
            EventSummary selected = filtered.stream()
                    .filter(event -> selectedEventId != null && event.id() == selectedEventId)
                    .findFirst().orElse(filtered.isEmpty() ? null : filtered.getFirst());
            if (selected != null) {
                selectedEventId = selected.id();
                events.getSelectionModel().select(selected);
            } else {
                selectedEventId = null;
                events.getSelectionModel().clearSelection();
                eventContent.getChildren().setAll(new Label("No matching events."));
            }
        } finally {
            refreshing = false;
        }
    }

    private Node eventDetails(EventDetails detail) {
        EventSummary event = detail.summary();
        Label title = new Label(event.name());
        title.getStyleClass().add("title");
        Label description = new Label(event.description());
        description.setWrapText(true);
        VBox content = new VBox(12,
                title,
                description,
                new Label("Status: " + pretty(event.status()) + "    Method: " + event.marketMethod().displayName()),
                new Label("Market maker: " + event.marketMakerName()
                        + "    Commission: " + event.commissionPercentage() + "% "
                        + event.commissionType().displayName()),
                new Label("Event account: " + money(event.accountBalance())
                        + "    Fees collected: " + money(detail.totalCommissionCollected())),
                actions(detail),
                section("Options", optionsTable(detail.options())));
        if (event.marketMethod() == MarketMethod.ORDER_BOOK) {
            content.getChildren().add(section("Open orders", orderTable(detail.orderBook())));
        }
        content.getChildren().add(section("Participants", participantTable(detail.participants())));
        content.getChildren().add(section("Trade history", tradeTable(detail.tradeHistory())));
        if (detail.settlement() != null) {
            content.getChildren().add(new Label("Winner: " + detail.settlement().winningOptionName()
                    + "    Paid: " + money(detail.settlement().payoutAfterCommission())));
        }
        return content;
    }

    private Node actions(EventDetails detail) {
        EventSummary event = detail.summary();
        boolean maker = userName.equalsIgnoreCase(event.marketMakerName());
        boolean blocked = snapshot.account().summary().blocked();
        Button open = new Button("Open event");
        open.setDisable(!maker || blocked || event.status() != EventStatus.INACTIVE);
        open.setOnAction(action -> execute(() -> client.open(event.id())));

        ComboBox<OptionDetails> winner = optionBox(detail.options());
        winner.setPromptText("Winner");
        Button close = new Button("Close event");
        close.setDisable(!maker || blocked || event.status() != EventStatus.ACTIVE);
        close.setOnAction(action -> {
            try {
                int winningOption = requiredOption(winner);
                execute(() -> client.close(event.id(), winningOption));
            } catch (IllegalArgumentException error) {
                showError(error.getMessage());
            }
        });
        FlowPane lifecycle = new FlowPane(8, 8, open, winner, close);

        ComboBox<OptionDetails> option = optionBox(detail.options());
        option.setPromptText("Option");
        TextField quantity = new TextField();
        quantity.setPromptText("Quantity");
        quantity.setPrefColumnCount(7);
        Button submit = new Button(event.marketMethod() == MarketMethod.LMSR ? "Buy shares" : "Submit order");
        submit.getStyleClass().add("primary");
        submit.setDisable(blocked || event.status() != EventStatus.ACTIVE);
        FlowPane trade = new FlowPane(8, 8, option, quantity);
        if (event.marketMethod() == MarketMethod.LMSR) {
            submit.setOnAction(action -> {
                try {
                    int selectedOption = requiredOption(option);
                    long shares = positiveLong(quantity, "Quantity");
                    execute(() -> client.buy(event.id(), selectedOption, shares));
                } catch (IllegalArgumentException error) {
                    showError(error.getMessage());
                }
            });
        } else {
            ComboBox<OrderSide> side = new ComboBox<>(FXCollections.observableArrayList(OrderSide.values()));
            side.setPromptText("Buy / sell");
            TextField price = new TextField();
            price.setPromptText("Price");
            price.setPrefColumnCount(7);
            trade.getChildren().addAll(side, price);
            submit.setOnAction(action -> {
                try {
                    if (side.getValue() == null) throw new IllegalArgumentException("Choose BUY or SELL.");
                    int selectedOption = requiredOption(option);
                    OrderSide selectedSide = side.getValue();
                    long shares = positiveLong(quantity, "Quantity");
                    double orderPrice = positiveDouble(price, "Price");
                    execute(() -> client.order(event.id(), selectedOption, selectedSide, shares, orderPrice));
                } catch (IllegalArgumentException error) {
                    showError(error.getMessage());
                }
            });
        }
        trade.getChildren().add(submit);
        VBox box = new VBox(9, new Label("Actions"), lifecycle, trade);
        box.getStyleClass().add("panel");
        return box;
    }

    private TableView<OptionDetails> optionsTable(List<OptionDetails> data) {
        TableView<OptionDetails> table = compact(data);
        table.getColumns().add(column("Option", OptionDetails::name, 145));
        table.getColumns().add(column("Price", item -> optionalMoney(item.currentPrice()), 85));
        table.getColumns().add(column("Issued", item -> Long.toString(item.sharesIssued()), 75));
        table.getColumns().add(column("Best bid", item -> optionalMoney(item.bestBid()), 85));
        table.getColumns().add(column("Best ask", item -> optionalMoney(item.bestAsk()), 85));
        table.getColumns().add(column("Spread", item -> optionalMoney(item.spread()), 85));
        return table;
    }

    private TableView<OrderDetails> orderTable(List<OrderDetails> data) {
        TableView<OrderDetails> table = compact(data);
        table.getColumns().add(column("Option", OrderDetails::optionName, 120));
        table.getColumns().add(column("Side", item -> item.side().name(), 70));
        table.getColumns().add(column("User", OrderDetails::userName, 120));
        table.getColumns().add(column("Quantity", item -> Long.toString(item.quantity()), 80));
        table.getColumns().add(column("Price", item -> money(item.price()), 80));
        return table;
    }

    private TableView<ParticipantDetails> participantTable(List<ParticipantDetails> data) {
        TableView<ParticipantDetails> table = compact(data);
        table.getColumns().add(column("User", ParticipantDetails::userName, 120));
        table.getColumns().add(column("Holdings", item -> holdings(item.holdings()), 220));
        table.getColumns().add(column("P / L", item -> money(item.profitLoss()), 90));
        return table;
    }

    private TableView<TradeDetails> tradeTable(List<TradeDetails> data) {
        TableView<TradeDetails> table = compact(data);
        table.getColumns().add(column("Option", TradeDetails::optionName, 120));
        table.getColumns().add(column("Qty", item -> Long.toString(item.quantity()), 60));
        table.getColumns().add(column("Price", item -> money(item.pricePerShare()), 80));
        table.getColumns().add(column("Fee", item -> money(item.commission()), 80));
        table.getColumns().add(column("Buyer", item -> item.buyerName(), 100));
        table.getColumns().add(column("Seller", item -> item.sellerName(), 100));
        return table;
    }

    private void chooseUpload(Button button) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Upload Exercise 3 XML");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        button.setDisable(true);
        statusLabel.setText("Uploading " + file.getName() + "...");
        runAsync(() -> client.upload(file), message -> {
            button.setDisable(false);
            statusLabel.setText(message);
            pullOnce();
        }, error -> {
            button.setDisable(false);
            showError(error.getMessage());
        });
    }

    private void execute(Work<String> action) {
        runAsync(action, message -> {
            statusLabel.setText(message);
            currentEvent = null;
            pullOnce();
        }, error -> showError(error.getMessage()));
    }

    private <T> void runAsync(Work<T> work, java.util.function.Consumer<T> success,
                              java.util.function.Consumer<RuntimeException> failure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.run();
            }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            failure.accept(error instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException(error.getMessage(), error));
        });
        Thread thread = new Thread(task, "guess-market-request");
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(stage);
        alert.setHeaderText("Action rejected");
        alert.showAndWait();
    }

    private <T> TableView<T> compact(List<T> data) {
        TableView<T> table = new TableView<>(FXCollections.observableArrayList(data));
        setupTable(table);
        table.setPrefHeight(Math.max(150, Math.min(260, 42 + data.size() * 28)));
        return table;
    }

    private <T> void setupTable(TableView<T> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setMinHeight(130);
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> text, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                text.apply(cell.getValue()) == null ? "-" : text.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private VBox section(String title, Node content) {
        VBox box = new VBox(8, new Label(title), content);
        box.getStyleClass().add("panel");
        return box;
    }

    private ComboBox<OptionDetails> optionBox(List<OptionDetails> options) {
        ComboBox<OptionDetails> box = new ComboBox<>(FXCollections.observableArrayList(options));
        box.setPrefWidth(160);
        box.setConverter(new StringConverter<>() {
            @Override public String toString(OptionDetails option) {
                return option == null ? "" : option.name();
            }
            @Override public OptionDetails fromString(String text) { return null; }
        });
        return box;
    }

    private int requiredOption(ComboBox<OptionDetails> options) {
        if (options.getValue() == null) throw new IllegalArgumentException("Choose an option.");
        return options.getValue().number();
    }

    private long positiveLong(TextField field, String label) {
        try {
            long number = Long.parseLong(field.getText().trim());
            if (number > 0) return number;
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(label + " must be a positive whole number.");
    }

    private double positiveDouble(TextField field, String label) {
        try {
            double number = Double.parseDouble(field.getText().trim());
            if (Double.isFinite(number) && number > 0) return number;
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(label + " must be a positive number.");
    }

    private String holdings(List<HoldingDetails> values) {
        return values.stream().filter(value -> value.shares() != 0)
                .map(value -> value.optionName() + ": " + value.shares())
                .reduce((first, second) -> first + ", " + second).orElse("-");
    }

    private String money(double value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    private String signedMoney(double value) {
        return (value > 0 ? "+" : "") + money(value);
    }

    private String optionalMoney(Double value) {
        return value == null ? "-" : money(value);
    }

    private String pretty(Enum<?> value) {
        String lower = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private Node spacer() {
        javafx.scene.layout.Region space = new javafx.scene.layout.Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        return space;
    }

    private void style(Scene scene) {
        scene.getStylesheets().add(getClass().getResource("/com/guessmarket/client/client.css").toExternalForm());
    }

    @Override
    public void stop() {
        puller.shutdownNow();
        try {
            client.logout();
        } catch (RuntimeException ignored) {
            // A stopped server cannot receive logout; the short session lease will expire.
        }
        Platform.exit();
    }

    private record PullResult(MarketSnapshot market, EventDetails event) {
    }

    @FunctionalInterface
    private interface Work<T> {
        T run();
    }
}
