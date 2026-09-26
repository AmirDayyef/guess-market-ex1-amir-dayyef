package com.guessmarket.server;

import com.guessmarket.api.dto.EventStatus;
import com.guessmarket.api.exception.InvalidMarketFileException;
import com.guessmarket.api.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarketSessionServiceTest {
    private static final String EVENT = """
            <GM-event name="Rain tomorrow">
              <description>Will it rain tomorrow?</description>
              <commission type="on-purchase">5</commission>
              <GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>
              <GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method>
            </GM-event>
            """;

    @Test
    void twoClientsShareEventsButCannotImpersonateEachOther() {
        MarketSessionService service = new MarketSessionService();
        String alice = service.login("Alice").token();
        String bob = service.login("Bob").token();
        assertThrows(InvalidOperationException.class, () -> service.login("aLiCe"));

        service.deposit(alice, 500.0);
        service.deposit(bob, 300.0);
        assertEquals("Added 1 event(s). You are their market maker.", service.upload(alice, bytes(market(EVENT))));
        assertEquals(1, service.snapshot(bob).events().size());
        assertFalse(service.snapshot(bob).users().stream().filter(user -> user.name().equals("Bob"))
                .findFirst().orElseThrow().marketMaker());
        assertThrows(InvalidOperationException.class, () -> service.open(bob, 1));

        service.open(alice, 1);
        assertEquals(EventStatus.ACTIVE, service.snapshot(bob).events().getFirst().status());
        service.buy(bob, 1, 1, 10);
        assertEquals(10, service.eventDetails(alice, 1).options().getFirst().sharesIssued());
        assertEquals("Bob", service.snapshot(bob).account().summary().name());
        assertTrue(service.snapshot(alice).accountEntries().stream()
                .anyMatch(entry -> entry.description().contains("purchase")));
        assertTrue(service.snapshot(bob).accountEntries().stream()
                .anyMatch(entry -> entry.description().contains("purchase")));
    }

    @Test
    void uploadRejectsDuplicateWithoutPartiallyAddingEvents() {
        MarketSessionService service = new MarketSessionService();
        String token = service.login("Maker").token();
        service.upload(token, bytes(market(EVENT)));
        String second = EVENT.replace("Rain tomorrow", "Another event");
        assertThrows(InvalidMarketFileException.class,
                () -> service.upload(token, bytes(market(second + EVENT))));
        assertEquals(1, service.snapshot(token).events().size());
        assertThrows(InvalidMarketFileException.class,
                () -> service.upload(token, bytes(market(EVENT.replace("</GM-event>", "<id>7</id></GM-event>")))));
        assertEquals(1, service.snapshot(token).events().size());
    }

    @Test
    void logoutAllowsSameNameToReturnToExistingAccount() {
        MarketSessionService service = new MarketSessionService();
        String first = service.login("Alice").token();
        service.deposit(first, 25);
        service.logout(first);
        String second = service.login("alice").token();
        assertEquals("Alice", service.snapshot(second).account().summary().name());
        assertEquals(25, service.snapshot(second).account().summary().balance());
    }

    @Test
    void officialExercise3SamplesLoadWhenAvailable() throws IOException {
        String sampleDirectory = System.getenv("GM_EX3_SAMPLES");
        Assumptions.assumeTrue(sampleDirectory != null && Files.isDirectory(Path.of(sampleDirectory)));
        MarketSessionService service = new MarketSessionService();
        String token = service.login("Maker").token();
        try (var small = Files.newInputStream(Path.of(sampleDirectory, "small.xml"));
             var multiple = Files.newInputStream(Path.of(sampleDirectory, "multiple.xml"))) {
            service.upload(token, small);
            service.upload(token, multiple);
        }
        assertEquals(4, service.snapshot(token).events().size());
        assertEquals("Mujtaba is Dead", service.snapshot(token).events().getFirst().name());
    }

    private static ByteArrayInputStream bytes(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static String market(String events) {
        return "<Guess-Market><GM-events>" + events + "</GM-events></Guess-Market>";
    }
}
