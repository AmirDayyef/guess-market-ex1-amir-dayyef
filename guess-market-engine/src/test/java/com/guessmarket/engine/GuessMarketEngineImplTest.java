package com.guessmarket.engine;

import com.guessmarket.api.dto.EventDetails;
import com.guessmarket.api.dto.EventStatus;
import com.guessmarket.api.dto.TradeReceipt;
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
    private static final double TOLERANCE = 0.0000001;

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsValidFileAndInitializesEveryEvent() throws Exception {
        GuessMarketEngineImpl engine = new GuessMarketEngineImpl();
        engine.loadMarketFromXml(writeXml("multiple.xml", marketXml(
                eventXml(1, "First Event", 5, "on-purchase", 100, "Yes", "No"),
                eventXml(2, "Second Event", 15, "on-close", 50, "Up", "Down"))));

        assertTrue(engine.hasLoadedMarket());
        assertEquals(2, engine.getEvents().size());
        EventDetails first = engine.getEventDetails(1);
        assertEquals(EventStatus.ACTIVE, first.summary().status());
        assertEquals(initialSubsidy(100), first.accountBalance(), TOLERANCE);
        assertEquals(0.5, first.options().get(0).currentPrice(), TOLERANCE);
        assertEquals(0.5, first.options().get(1).currentPrice(), TOLERANCE);
    }

    @Test
    void performsLmsrTradesUsingFullPrecision() throws Exception {
        GuessMarketEngineImpl engine = engineWithSingleEvent(5, "on-purchase", 100);

        TradeReceipt first = engine.buyShares(1, 1, 100);
        TradeReceipt second = engine.buyShares(1, 2, 1_000);
        EventDetails details = second.updatedEvent();

        assertEquals(62.0114506958277, first.sharesCost(), TOLERANCE);
        assertEquals(3.10057253479139, first.commission(), TOLERANCE);
        assertEquals(65.1120232306191, first.totalPaid(), TOLERANCE);
        assertEquals(868.686171467148, second.sharesCost(), TOLERANCE);
        assertEquals(43.4343085733574, second.commission(), TOLERANCE);
        assertEquals(initialSubsidy(100) + 977.232503271124, details.accountBalance(), TOLERANCE);
        assertEquals(46.5348811081489, details.totalCommissionCollected(), TOLERANCE);
        assertEquals(0.000123394575986, details.options().get(0).currentPrice(), TOLERANCE);
        assertEquals(0.999876605424014, details.options().get(1).currentPrice(), TOLERANCE);
        assertEquals(2, details.tradeHistory().size());
        assertEquals("No", details.tradeHistory().get(0).optionName());
        assertEquals("Yes", details.tradeHistory().get(1).optionName());
    }

    @Test
    void closesOnPurchaseEventAndKeepsRemainingSubsidyBalance() throws Exception {
        GuessMarketEngineImpl engine = engineWithSingleEvent(5, "on-purchase", 100);
        engine.buyShares(1, 1, 100);
        engine.buyShares(1, 2, 1_000);

        EventDetails closed = engine.closeEvent(1, 2);

        assertEquals(EventStatus.CLOSED, closed.summary().status());
        assertEquals("No", closed.settlement().winningOptionName());
        assertEquals(1_000, closed.settlement().winningShares());
        assertEquals(initialSubsidy(100) - 22.767496728876, closed.accountBalance(), TOLERANCE);
        assertEquals(closed.accountBalance(), closed.settlement().finalAccountBalance(), TOLERANCE);
        assertThrows(InvalidOperationException.class, () -> engine.buyShares(1, 1, 1));
        assertThrows(InvalidOperationException.class, () -> engine.closeEvent(1, 1));
    }

    @Test
    void chargesOnCloseCommissionOnlyAtSettlement() throws Exception {
        GuessMarketEngineImpl engine = engineWithSingleEvent(15, "on-close", 50);
        TradeReceipt receipt = engine.buyShares(1, 1, 100);

        assertEquals(0.0, receipt.commission(), TOLERANCE);
        assertEquals(initialSubsidy(50) + 71.6890415241514,
                receipt.updatedEvent().accountBalance(), TOLERANCE);

        EventDetails closed = engine.closeEvent(1, 1);
        assertEquals(15.0, closed.settlement().commission(), TOLERANCE);
        assertEquals(85.0, closed.settlement().payoutAfterCommission(), TOLERANCE);
        assertEquals(initialSubsidy(50) - 13.3109584758486, closed.accountBalance(), TOLERANCE);
        assertEquals(15.0, closed.totalCommissionCollected(), TOLERANCE);
    }

    @Test
    void failedLoadPreservesPreviousValidState() throws Exception {
        GuessMarketEngineImpl engine = engineWithSingleEvent(5, "on-purchase", 100);
        engine.buyShares(1, 1, 25);
        double balanceBefore = engine.getEventDetails(1).accountBalance();
        String invalid = marketXml(
                eventXml(7, "Duplicate A", 5, "on-purchase", 100, "A", "B"),
                eventXml(7, "Duplicate B", 10, "on-close", 80, "C", "D"));

        assertThrows(InvalidMarketFileException.class,
                () -> engine.loadMarketFromXml(writeXml("duplicates.xml", invalid)));

        assertEquals(1, engine.getEvents().size());
        assertEquals(balanceBefore, engine.getEventDetails(1).accountBalance(), TOLERANCE);
        assertEquals(1, engine.getEventDetails(1).tradeHistory().size());
    }

    @Test
    void rejectsOutOfRangeCommissionAndNonPositiveLiquidity() throws Exception {
        GuessMarketEngineImpl engine = new GuessMarketEngineImpl();
        String badCommission = marketXml(eventXml(1, "Bad Fee", 115, "on-purchase", 100, "A", "B"));
        String badLiquidity = marketXml(eventXml(2, "Bad B", 5, "on-close", 0, "A", "B"));

        assertThrows(InvalidMarketFileException.class,
                () -> engine.loadMarketFromXml(writeXml("bad-fee.xml", badCommission)));
        assertThrows(InvalidMarketFileException.class,
                () -> engine.loadMarketFromXml(writeXml("bad-b.xml", badLiquidity)));
    }

    @Test
    void acceptsBothOfficialTypoAndCorrectCommissionSpelling() throws Exception {
        GuessMarketEngineImpl engine = new GuessMarketEngineImpl();
        String xml = marketXml(eventXml(1, "Correct Spelling", 5, "on-purchase", 100, "A", "B")
                .replace("comision", "commission"));

        engine.loadMarketFromXml(writeXml("correct-spelling.xml", xml));

        assertEquals(1, engine.getEvents().size());
    }

    @Test
    void saveAndLoadRestoresCompleteHistoryAndSettlement() throws Exception {
        GuessMarketEngineImpl original = engineWithSingleEvent(5, "on-purchase", 100);
        original.buyShares(1, 1, 20);
        EventDetails expected = original.closeEvent(1, 1);
        Path statePath = temporaryDirectory.resolve("saved market");
        original.saveState(statePath.toString());

        GuessMarketEngineImpl restored = new GuessMarketEngineImpl();
        restored.loadState(statePath.toString());
        EventDetails actual = restored.getEventDetails(1);

        assertFalse(restored.getActiveEvents().stream().findAny().isPresent());
        assertEquals(expected.summary(), actual.summary());
        assertEquals(expected.accountBalance(), actual.accountBalance(), TOLERANCE);
        assertEquals(expected.tradeHistory(), actual.tradeHistory());
        assertEquals(expected.settlement(), actual.settlement());
    }

    @Test
    void validatesQuantitiesAndOptions() throws Exception {
        GuessMarketEngineImpl engine = engineWithSingleEvent(5, "on-purchase", 100);

        assertThrows(InvalidOperationException.class, () -> engine.buyShares(1, 1, 0));
        assertThrows(InvalidOperationException.class, () -> engine.buyShares(1, 1, -1));
        assertThrows(InvalidOperationException.class, () -> engine.buyShares(1, 3, 1));
        assertThrows(InvalidOperationException.class, () -> engine.getEventDetails(999));
    }

    private GuessMarketEngineImpl engineWithSingleEvent(int commission, String type, int liquidity) throws Exception {
        GuessMarketEngineImpl engine = new GuessMarketEngineImpl();
        engine.loadMarketFromXml(writeXml(
                "single.xml",
                marketXml(eventXml(1, "Test Event", commission, type, liquidity, "Yes", "No"))));
        return engine;
    }

    private static double initialSubsidy(int liquidity) {
        return liquidity * Math.log(2.0);
    }

    private String writeXml(String fileName, String content) throws Exception {
        Path file = temporaryDirectory.resolve(fileName);
        Files.writeString(file, content);
        return file.toString();
    }

    private String marketXml(String... events) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Guess-Market>
                  <GM-events>
                %s
                  </GM-events>
                </Guess-Market>
                """.formatted(String.join("\n", events));
    }

    private String eventXml(
            int id,
            String name,
            int commission,
            String commissionType,
            int liquidity,
            String firstOption,
            String secondOption) {
        return """
                    <GM-event name="%s">
                      <id>%d</id>
                      <description>Description for %s</description>
                      <comision type="%s">%d</comision>
                      <GM-options>
                        <GM-option>%s</GM-option>
                        <GM-option>%s</GM-option>
                      </GM-options>
                      <GM-method><GM-LMSR><b>%d</b></GM-LMSR></GM-method>
                    </GM-event>
                """.formatted(name, id, name, commissionType, commission, firstOption, secondOption, liquidity);
    }
}
