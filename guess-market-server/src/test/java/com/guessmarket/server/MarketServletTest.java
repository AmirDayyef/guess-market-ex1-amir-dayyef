package com.guessmarket.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MarketServletTest {
    @TempDir Path directory;

    @Test
    void clientsExchangeJsonThroughTomcatAndShareUploadedEvents() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.toString());
        tomcat.setPort(0);
        Context context = tomcat.addContext("/guess-market-server", directory.toString());
        Tomcat.addServlet(context, "market", new MarketServlet());
        context.addServletMappingDecoded("/api/*", "market");
        tomcat.start();
        int port = tomcat.getConnector().getLocalPort();
        HttpClient http = HttpClient.newHttpClient();
        URI api = URI.create("http://localhost:" + port + "/guess-market-server/api/");
        try {
            JsonObject login = json(post(http, api.resolve("login"), "", "{\"name\":\"Alice\"}"));
            String token = login.get("token").getAsString();
            assertTrue(!token.isBlank());
            assertEquals(400, post(http, api.resolve("login"), "", "{\"name\":\"aLiCe\"}").statusCode());

            String xml = "<Guess-Market><GM-events><GM-event name=\"Rain\">"
                    + "<description>Rain?</description><commission type=\"on-close\">10</commission>"
                    + "<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>"
                    + "<GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method>"
                    + "</GM-event></GM-events></Guess-Market>";
            assertEquals(200, post(http, api.resolve("upload"), token, xml).statusCode());
            HttpResponse<String> snapshot = get(http, api.resolve("snapshot"), token);
            assertEquals(200, snapshot.statusCode());
            assertEquals("Rain", json(snapshot).getAsJsonArray("events")
                    .get(0).getAsJsonObject().get("name").getAsString());
            assertEquals(400, get(http, api.resolve("snapshot"), "").statusCode());
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    private HttpResponse<String> post(HttpClient http, URI uri, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!token.isBlank()) request.header("X-Session-Token", token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient http, URI uri, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET();
        if (!token.isBlank()) request.header("X-Session-Token", token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
