/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.commons.spi.structured.oaipmh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viglet.dumont.connector.commons.http.DumHttpRetry;
import com.viglet.dumont.connector.commons.text.DumTextExtractor;

/** The DSpace REST client (roadmap D314), against an in-process server. */
class DumHttpDSpaceRestTest {

    private static final String HOST = "127.0.0.1";

    private HttpServer server;
    private final List<String> served = new ArrayList<>();
    private final List<Long> paced = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(HOST, 0), 0);
        server.createContext("/api/pid/find", exchange -> redirect(exchange, "/api/core/items/u1"));
        server.createContext("/api/core/items/u1", exchange -> respond(exchange, 200, "application/hal+json",
                "{\"uuid\":\"u1\",\"withdrawn\":false}"));
        server.createContext("/api/core/items/private", exchange -> respond(exchange, 401, "application/json", "{}"));
        server.createContext("/api/away", exchange -> redirect(exchange,
                "http://localhost:" + server.getAddress().getPort() + "/api/core/items/u1"));
        server.createContext("/api/core/bitstreams/t/content", exchange -> respond(exchange, 200,
                "text/plain;charset=UTF-8", "Texto extraído pelo DSpace."));
        server.createContext("/api/core/bitstreams/html/content", exchange -> respond(exchange, 200,
                "text/html", "<html>landing</html>"));
        server.createContext("/api/core/bitstreams/embargo/content", exchange -> respond(exchange, 403,
                "application/json", "{}"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void followsTheHandleRedirectOnTheSameHost() throws IOException {
        DumDSpaceRest.Response response = rest().get(url("/api/pid/find?id=123/4"), HOST);

        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"u1\""));
        assertEquals(List.of("/api/pid/find", "/api/core/items/u1"), served);
    }

    @Test
    void aPrivateItemIsAnAnswerNotAnException() throws IOException {
        DumDSpaceRest.Response response = rest().get(url("/api/core/items/private"), HOST);

        assertTrue(response.denied());
    }

    @Test
    void aRedirectOffTheHostIsNeverFollowed() {
        assertThrows(IOException.class, () -> rest().get(url("/api/away"), HOST));
        assertEquals(List.of("/api/away"), served);
    }

    @Test
    void readsPlainTextAndRefusesALandingPage() throws IOException {
        assertEquals(Optional.of("Texto extraído pelo DSpace."), rest().text(url("/api/core/bitstreams/t/content"), HOST));
        assertEquals(Optional.empty(), rest().text(url("/api/core/bitstreams/html/content"), HOST));
    }

    @Test
    void anEmbargoedBitstreamFailsTheRead() {
        IOException e = assertThrows(IOException.class,
                () -> rest().text(url("/api/core/bitstreams/embargo/content"), HOST));
        assertTrue(e.getMessage().contains("HTTP 403"), e.getMessage());
    }

    @Test
    void everyCallIsPaced() throws IOException {
        DumHttpDSpaceRest rest = rest();
        rest.get(url("/api/core/items/u1"), HOST);
        rest.text(url("/api/core/bitstreams/t/content"), HOST);

        assertEquals(List.of(500L, 500L), paced);
    }

    private DumHttpDSpaceRest rest() {
        return new DumHttpDSpaceRest(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                new DumHttpRetry(ms -> {
                }, 0), paced::add, Duration.ofMillis(500), DumTextExtractor.withDefaults());
    }

    private String url(String path) {
        return "http://" + HOST + ":" + server.getAddress().getPort() + path;
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        served.add(exchange.getRequestURI().getPath());
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        served.add(exchange.getRequestURI().getPath());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
