package com.guessmarket.server;

import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.Context;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

final class WarDeploymentTest {
    @TempDir Path directory;

    @Test
    void packagedWarDeploysWithoutExternalLibraries() throws Exception {
        String warFile = System.getenv("GM_EX3_WAR");
        Assumptions.assumeTrue(warFile != null && Files.isRegularFile(Path.of(warFile)));
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.toString());
        Files.createDirectories(directory.resolve("webapps"));
        tomcat.setPort(0);
        Context context = tomcat.addWebapp("/guess-market-server", Path.of(warFile).toAbsolutePath().toString());
        tomcat.start();
        try {
            Class<?> deployedServlet = context.getLoader().getClassLoader()
                    .loadClass("com.guessmarket.server.MarketServlet");
            assertNotSame(MarketServlet.class.getClassLoader(), deployedServlet.getClassLoader());
            int port = tomcat.getConnector().getLocalPort();
            HttpRequest login = HttpRequest.newBuilder(URI.create(
                            "http://localhost:" + port + "/guess-market-server/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Smoke Tester\"}"))
                    .build();
            assertEquals(200, HttpClient.newHttpClient()
                    .send(login, HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }
}
