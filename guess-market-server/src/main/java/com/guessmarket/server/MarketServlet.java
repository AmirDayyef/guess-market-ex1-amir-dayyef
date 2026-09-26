package com.guessmarket.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.guessmarket.api.dto.OrderSide;
import com.guessmarket.api.exception.GuessMarketException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@WebServlet("/api/*")
public final class MarketServlet extends HttpServlet {
    private static final int MAX_REQUEST_BYTES = 2_000_000;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private MarketSessionService market;

    @Override
    public void init() throws ServletException {
        synchronized (getServletContext()) {
            Object existing = getServletContext().getAttribute(MarketSessionService.class.getName());
            if (existing == null) {
                existing = new MarketSessionService();
                getServletContext().setAttribute(MarketSessionService.class.getName(), existing);
            }
            market = (MarketSessionService) existing;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String token = request.getHeader("X-Session-Token");
            Object result = switch (path(request)) {
                case "/snapshot" -> market.snapshot(token);
                case "/event" -> market.eventDetails(token,
                        Integer.parseInt(request.getParameter("id")));
                default -> throw new IllegalArgumentException("Unknown endpoint.");
            };
            send(response, HttpServletResponse.SC_OK, result);
        } catch (RuntimeException exception) {
            sendError(response, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String path = path(request);
            String token = request.getHeader("X-Session-Token");
            if ("/upload".equals(path)) {
                byte[] xml = readLimited(request);
                String message = market.upload(token, new ByteArrayInputStream(xml));
                send(response, HttpServletResponse.SC_OK, new ActionResult(message));
                return;
            }

            JsonObject body = JsonParser.parseString(new String(readLimited(request), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Object result = switch (path) {
                case "/login" -> market.login(string(body, "name"));
                case "/logout" -> {
                    market.logout(token);
                    yield new ActionResult("Signed out.");
                }
                case "/deposit" -> new ActionResult(market.deposit(token, number(body, "amount")));
                case "/open" -> new ActionResult(market.open(token, integer(body, "eventId")));
                case "/buy" -> new ActionResult(market.buy(token, integer(body, "eventId"),
                        integer(body, "option"), whole(body, "quantity")));
                case "/order" -> new ActionResult(market.order(token, integer(body, "eventId"),
                        integer(body, "option"), orderSide(body), whole(body, "quantity"),
                        number(body, "price")));
                case "/close" -> new ActionResult(market.close(token, integer(body, "eventId"),
                        integer(body, "winner")));
                default -> throw new IllegalArgumentException("Unknown endpoint.");
            };
            send(response, HttpServletResponse.SC_OK, result);
        } catch (RuntimeException exception) {
            sendError(response, exception);
        }
    }

    private String path(HttpServletRequest request) {
        return request.getPathInfo() == null ? "" : request.getPathInfo();
    }

    private byte[] readLimited(HttpServletRequest request) throws IOException {
        byte[] content = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (content.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Uploaded content exceeds 2 MB.");
        }
        return content;
    }

    private String string(JsonObject body, String key) {
        return body.get(key).getAsString();
    }

    private int integer(JsonObject body, String key) {
        return body.get(key).getAsInt();
    }

    private long whole(JsonObject body, String key) {
        return body.get(key).getAsLong();
    }

    private double number(JsonObject body, String key) {
        return body.get(key).getAsDouble();
    }

    private OrderSide orderSide(JsonObject body) {
        return OrderSide.valueOf(string(body, "side").toUpperCase(Locale.ROOT));
    }

    private void send(HttpServletResponse response, int status, Object result) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        gson.toJson(result, response.getWriter());
    }

    private void sendError(HttpServletResponse response, RuntimeException exception) throws IOException {
        int status = exception instanceof GuessMarketException || exception instanceof IllegalArgumentException
                || exception instanceof JsonParseException || exception instanceof IllegalStateException
                || exception instanceof NullPointerException
                ? HttpServletResponse.SC_BAD_REQUEST : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        String message = status == HttpServletResponse.SC_BAD_REQUEST
                ? exception.getMessage() : "The server could not complete the request.";
        send(response, status, new ErrorResult(message == null ? "Invalid request." : message));
    }

    private record ActionResult(String message) {
    }

    private record ErrorResult(String error) {
    }
}
