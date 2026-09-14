package com.guessmarket.engine;

import com.guessmarket.api.dto.EventDetails;
import com.guessmarket.api.dto.EventStatus;
import com.guessmarket.api.dto.MarketMethod;
import com.guessmarket.api.dto.OrderReceipt;
import com.guessmarket.api.dto.OrderSide;
import com.guessmarket.api.dto.TradeKind;
import com.guessmarket.api.dto.UserDetails;
import com.guessmarket.api.exception.InvalidMarketFileException;
import com.guessmarket.api.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuessMarketEngineImplTest {
    private static final double TOLERANCE = 0.000001;

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsExerciseTwoUsersAndBothMarketMethodsAsInactive() throws Exception {
        GuessMarketEngineImpl engine = loadValidMarket();

        assertTrue(engine.hasLoadedMarket());
        assertEquals(2, engine.getEvents().size());
        assertEquals(4, engine.getUsers().size());
        assertEquals(EventStatus.INACTIVE, engine.getEventDetails(1).summary().status());
        assertEquals(MarketMethod.LMSR, engine.getEventDetails(1).summary().marketMethod());
        assertEquals(MarketMethod.ORDER_BOOK, engine.getEventDetails(2).summary().marketMethod());
        assertEquals(0.0, engine.getEventDetails(1).summary().accountBalance(), TOLERANCE);
        assertEquals("Tikva", engine.getEventDetails(1).summary().marketMakerName());
        assertEquals("Zoe", engine.getEventDetails(2).summary().marketMakerName());
    }

    @Test
    void rejectsTheTwoOfficialExerciseTwoErrorCases() throws Exception {
        GuessMarketEngineImpl engine = new GuessMarketEngineImpl();
        String zeroCash = validXml().replace("<initial-cash>200</initial-cash>", "<initial-cash>0</initial-cash>");
        String missingEvent = validXml().replace("<event id=\"2\"/>", "<event id=\"12\"/>");

        InvalidMarketFileException cashError = assertThrows(
                InvalidMarketFileException.class,
                () -> engine.loadMarketFromXml(writeXml("zero-cash.xml", zeroCash)));
        InvalidMarketFileException mmError = assertThrows(
                InvalidMarketFileException.class,
                () -> engine.loadMarketFromXml(writeXml("bad-mm.xml", missingEvent)));

        assertTrue(cashError.getMessage().contains("greater than 0"));
        assertTrue(mmError.getMessage().contains("does not exist"));
    }

    @Test
    void failedLoadKeepsThePreviousValidMarket() throws Exception {
        GuessMarketEngineImpl engine = loadValidMarket();
        String invalid = validXml().replace("<initial-cash>200</initial-cash>", "<initial-cash>0</initial-cash>");

        assertThrows(InvalidMarketFileException.class,
                () -> engine.loadMarketFromXml(writeXml("invalid.xml", invalid)));

        assertEquals(2, engine.getEvents().size());
        assertEquals("Rain tomorrow", engine.getEventDetails(1).summary().name());
    }

    @Test
    void onlyTheMarketMakerCanOpenAndCloseAnEvent() throws Exception {
        GuessMarketEngineImpl engine = loadValidMarket();

        assertThrows(InvalidOperationException.class, () -> engine.openEvent("Alice", 1));
        EventDetails opened = engine.openEvent("Tikva", 1);
        assertEquals(EventStatus.ACTIVE, opened.summary().status());
        assertEquals(100.0 * Math.log(2.0), opened.summary().accountBalance(), TOLERANCE);
        assertThrows(InvalidOperationException.class, () -> engine.closeEvent("Alice", 1, 1));
    }

    @Test
    void lmsrPurchaseMovesMoneySharesAndCommissionBetweenAccounts() throws Exception {
        GuessMarketEngineImpl engine = loadValidMarket();
        engine.openEvent("Tikva", 1);

        var receipt = engine.buyShares("Alice", 1, 1, 100);

        assertEquals(62.0114506958277, receipt.sharesCost(), TOLERANCE);
        assertEquals(3.10057253479139, receipt.commission(), TOLERANCE);
        assertEquals(134.887976769381, engine.getUserDetails("Alice").summary().balance(), TOLERANCE);
        assertEquals(9933.7858544788, engine.getUserDetails("Tikva").summary().balance(), TOLERANCE);
        assertEquals(100, engine.getUserDetails("Alice").events().getFirst().holdings().getFirst().shares());
    }

    @Test
    void openingOrderBookBuysTheInitialPairsForTheMarketMaker() throws Exception {
        GuessMarketEngineImpl engine = loadValidMarket();

        EventDetails opened = engine.openEvent("Zoe", 2);
        UserDetails zoe = engine.getUserDetails("Zoe");

        assertEquals(100.0, opened.summary().accountBalance(), TOLERANCE);
        assertEquals(400.0, zoe.summary().balance(), TOLERANCE);
        assertEquals(100, zoe.events().getFirst().holdings().get(0).shares());
        assertEquals(100, zoe.events().getFirst().holdings().get(1).shares());
    }

    @Test
    void reopeningAnEventDoesNotChargeTheMarketMakerAgain() throws Exception {
        GuessMarketEngineImpl engine = loadValidMarket();
        engine.openEvent("Zoe", 2);
        double balanceAfterOpening = engine.getUserDetails("Zoe").summary().balance();

        assertThrows(InvalidOperationException.class, () -> engine.openEvent("Zoe", 2));

        assertEquals(balanceAfterOpening, engine.getUserDetails("Zoe").summary().balance(), TOLERANCE);
    }

    @Test
    void orderBookMatchesAtTheRestingPriceAndSupportsPartialFills() throws Exception {
        GuessMarketEngineImpl engine = loadValidMarket();
        engine.openEvent("Zoe", 2);
        engine.submitOrder("Zoe", 2, 1, OrderSide.SELL, 25, 0.58);

        OrderReceipt receipt = engine.submitOrder("Alice", 2, 1, OrderSide.BUY, 40, 0.60);

        assertEquals(1, receipt.executions().size());
        assertEquals(TradeKind.ORDER_MATCH, receipt.executions().getFirst().kind());
        assertEquals(25, receipt.executions().getFirst().quantity());
        assertEquals(0.58, receipt.executions().getFirst().pricePerShare(), TOLERANCE);
        assertEquals(15, receipt.updatedEvent().orderBook().getFirst().quantity());
        assertEquals(OrderSide.BUY, receipt.updatedEvent().orderBook().getFirst().side());
        assertEquals(25, engine.getUserDetails("Alice").events().getFirst().holdings().getFirst().shares());
    }

    @Test
    void complementaryBuyOrdersMintPairsAndLeaveTheRemainderResting() throws Exception {
        GuessMarketEngineImpl engine = loadValidMarket();
        engine.openEvent("Zoe", 2);
        engine.submitOrder("Carol", 2, 2, OrderSide.BUY, 35, 0.42);

        OrderReceipt receipt = engine.submitOrder("Alice", 2, 1, OrderSide.BUY, 40, 0.62);

        assertEquals(2, receipt.executions().size());
        assertTrue(receipt.executions().stream().allMatch(trade -> trade.kind() == TradeKind.MINT));
        assertEquals(135.0, receipt.updatedEvent().summary().accountBalance(), TOLERANCE);
        assertEquals(1, receipt.updatedEvent().orderBook().size());
        assertEquals(5, receipt.updatedEvent().orderBook().getFirst().quantity());
        assertEquals(35, engine.getUserDetails("Alice").events().getFirst().holdings().getFirst().shares());
        assertEquals(35, engine.getUserDetails("Carol").events().getFirst().holdings().get(1).shares());
    }

    @Test
    void closingOrderBookPaysWinnersClearsOrdersAndEmptiesTheContract() throws Exception {
        GuessMarketEngineImpl engine = loadValidMarket();
        engine.openEvent("Zoe", 2);
        engine.submitOrder("Carol", 2, 2, OrderSide.BUY, 35, 0.42);
        engine.submitOrder("Alice", 2, 1, OrderSide.BUY, 40, 0.62);

        EventDetails closed = engine.closeEvent("Zoe", 2, 1);

        assertEquals(EventStatus.CLOSED, closed.summary().status());
        assertEquals(0.0, closed.summary().accountBalance(), TOLERANCE);
        assertTrue(closed.orderBook().isEmpty());
        assertEquals("YES", closed.settlement().winningOptionName());
        assertEquals(135.0, closed.settlement().grossPayout(), TOLERANCE);
        assertFalse(engine.getUserDetails("Alice").summary().blocked());
    }

    private GuessMarketEngineImpl loadValidMarket() throws Exception {
        GuessMarketEngineImpl engine = new GuessMarketEngineImpl();
        engine.loadMarketFromXml(writeXml("valid.xml", validXml()));
        return engine;
    }

    private String writeXml(String fileName, String content) throws Exception {
        Path file = temporaryDirectory.resolve(fileName);
        Files.writeString(file, content);
        return file.toString();
    }

    private String validXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Guess-Market>
                  <GM-events>
                    <GM-event name="Rain tomorrow">
                      <id>1</id>
                      <description>Will it rain tomorrow?</description>
                      <commission type="on-purchase">5</commission>
                      <GM-options>
                        <GM-option>YES</GM-option>
                        <GM-option>NO</GM-option>
                      </GM-options>
                      <GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method>
                    </GM-event>
                    <GM-event name="World Cup">
                      <id>2</id>
                      <description>Who wins the match?</description>
                      <commission type="on-purchase">1</commission>
                      <GM-options>
                        <GM-option>YES</GM-option>
                        <GM-option>NO</GM-option>
                      </GM-options>
                      <GM-method><GM-order-book allow-mint="true" initial="100" d="1"/></GM-method>
                    </GM-event>
                  </GM-events>
                  <GM-users>
                    <GM-user name="Tikva">
                      <initial-cash>10000</initial-cash>
                      <GM-market-maker><event id="1"/></GM-market-maker>
                    </GM-user>
                    <GM-user name="Zoe">
                      <initial-cash>500</initial-cash>
                      <GM-market-maker><event id="2"/></GM-market-maker>
                    </GM-user>
                    <GM-user name="Alice"><initial-cash>200</initial-cash></GM-user>
                    <GM-user name="Carol"><initial-cash>200</initial-cash></GM-user>
                  </GM-users>
                </Guess-Market>
                """;
    }
}
