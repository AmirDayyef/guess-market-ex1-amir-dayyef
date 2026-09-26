package com.guessmarket.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.guessmarket.api.dto.EventDetails;
import com.guessmarket.api.dto.MarketSnapshot;
import com.guessmarket.api.dto.OrderSide;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class HttpMarketClient {
    private static final URI DEFAULT_BASE = URI.create("http://localhost:8080/guess-market-server/api/");
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final Gson gson = new Gson();
    private final URI base;
    private volatile String token;

    HttpMarketClient() {
        this(URI.create(System.getProperty("guessmarket.serverUrl", DEFAULT_BASE.toString())));
    }

    HttpMarketClient(URI base) {
        this.base = base;
    }

    String login(String name) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        JsonObject result = JsonParser.parseString(send("login", body, false)).getAsJsonObject();
        token = result.get("token").getAsString();
        return result.get("userName").getAsString();
    }

    void logout() {
        if (token != null) {
            try {
                send("logout", new JsonObject(), true);
            } finally {
                token = null;
            }
        }
    }

    MarketSnapshot snapshot() {
        return gson.fromJson(get("snapshot"), MarketSnapshot.class);
    }

    EventDetails event(int id) {
        return gson.fromJson(get("event?id=" + id), EventDetails.class);
    }

    String upload(File file) {
        try {
            HttpRequest request = request("upload")
                    .header("Content-Type", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofFile(file.toPath())).build();
            return message(exchange(request));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + file.getName() + ": " + exception.getMessage(), exception);
        }
    }

    String deposit(double amount) {
        JsonObject body = new JsonObject();
        body.addProperty("amount", amount);
        return message(send("deposit", body, true));
    }

    String open(int eventId) {
        return eventAction("open", eventId, null, null, null, null, null);
    }

    String buy(int eventId, int option, long quantity) {
        return eventAction("buy", eventId, option, quantity, null, null, null);
    }

    String order(int eventId, int option, OrderSide side, long quantity, double price) {
        return eventAction("order", eventId, option, quantity, side, price, null);
    }

    String close(int eventId, int winner) {
        return eventAction("close", eventId, null, null, null, null, winner);
    }

    private String eventAction(String path, int eventId, Integer option, Long quantity,
                               OrderSide side, Double price, Integer winner) {
        JsonObject body = new JsonObject();
        body.addProperty("eventId", eventId);
        if (option != null) body.addProperty("option", option);
        if (quantity != null) body.addProperty("quantity", quantity);
        if (side != null) body.addProperty("side", side.name());
        if (price != null) body.addProperty("price", price);
        if (winner != null) body.addProperty("winner", winner);
        return message(send(path, body, true));
    }

    private String message(String response) {
        return JsonParser.parseString(response).getAsJsonObject().get("message").getAsString();
    }

    private String get(String path) {
        return exchange(request(path).GET().build());
    }

    private String send(String path, JsonObject body, boolean authenticated) {
        HttpRequest.Builder builder = authenticated ? request(path) : HttpRequest.newBuilder(base.resolve(path));
        HttpRequest request = builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8)).build();
        return exchange(request);
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(15));
        if (token != null) {
            builder.header("X-Session-Token", token);
        }
        return builder;
    }

    private String exchange(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                String error;
                try {
                    error = JsonParser.parseString(response.body()).getAsJsonObject().get("error").getAsString();
                } catch (RuntimeException exception) {
                    error = "Server returned HTTP " + response.statusCode() + ".";
                }
                throw new IllegalStateException(error);
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot reach Guess Market server at " + base
                    + ". Start Tomcat first. " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request was interrupted.", exception);
        }
    }
}
