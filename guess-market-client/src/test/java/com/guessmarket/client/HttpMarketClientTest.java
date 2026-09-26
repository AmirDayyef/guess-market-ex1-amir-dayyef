package com.guessmarket.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HttpMarketClientTest {
    @Test
    void loginAndSnapshotDeserializeWithoutTheEngineOnTheClient() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> suppliedToken = new AtomicReference<>();
        server.createContext("/api/login", exchange -> {
            byte[] result = "{\"token\":\"test-token\",\"userName\":\"Alice\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, result.length);
            exchange.getResponseBody().write(result);
            exchange.close();
        });
        server.createContext("/api/snapshot", exchange -> {
            suppliedToken.set(exchange.getRequestHeaders().getFirst("X-Session-Token"));
            String json = """
                    {"events":[],"users":[],"account":{"summary":{"name":"Alice",
                    "balance":25.0,"blocked":false,"marketMakerEventIds":[],
                    "participatingEventIds":[]},"events":[]},"accountEntries":[]}
                    """;
            byte[] result = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, result.length);
            exchange.getResponseBody().write(result);
            exchange.close();
        });
        server.start();
        try {
            HttpMarketClient client = new HttpMarketClient(URI.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/"));
            assertEquals("Alice", client.login("Alice"));
            assertEquals(25.0, client.snapshot().account().summary().balance());
            assertEquals("test-token", suppliedToken.get());
        } finally {
            server.stop(0);
        }
    }
}
