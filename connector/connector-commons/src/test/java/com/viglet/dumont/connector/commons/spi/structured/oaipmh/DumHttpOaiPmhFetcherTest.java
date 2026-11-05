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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viglet.dumont.connector.commons.http.DumHttpRetry;

/** The JDK client behind an OAI-PMH source (roadmap D315), against an in-process server. */
class DumHttpOaiPmhFetcherTest {

    private HttpServer server;
    private final AtomicInteger busyAnswers = new AtomicInteger();
    private final AtomicReference<String> userAgent = new AtomicReference<>();
    private final List<Long> sleeps = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Answers 503 with Retry-After: 7 as many times as busyAnswers says, then 200.
        server.createContext("/oai", exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            if (busyAnswers.getAndDecrement() > 0) {
                exchange.getResponseHeaders().add("Retry-After", "7");
                respond(exchange, 503, "busy");
            } else {
                respond(exchange, 200, "<OAI-PMH/>");
            }
        });
        server.createContext("/gone", exchange -> respond(exchange, 404, "no"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void waitsWhatRetryAfterAsksAndTriesAgain() throws IOException {
        busyAnswers.set(2);

        assertEquals("<OAI-PMH/>", fetcher(3).fetch(url("/oai?verb=Identify")));
        assertEquals(List.of(7_000L, 7_000L), sleeps, "slept the asked seconds, without real waiting");
        assertEquals(DumHttpOaiPmhFetcher.USER_AGENT, userAgent.get());
    }

    @Test
    void givesUpAfterTheRetryBound() {
        busyAnswers.set(10);

        IOException e = assertThrows(IOException.class, () -> fetcher(2).fetch(url("/oai?verb=Identify")));
        assertTrue(e.getMessage().contains("HTTP 503"), e.getMessage());
        assertEquals(2, sleeps.size());
    }

    @Test
    void anyOtherStatusFailsAtOnce() {
        IOException e = assertThrows(IOException.class, () -> fetcher(3).fetch(url("/gone?resumptionToken=x")));
        assertTrue(e.getMessage().contains("HTTP 404"), e.getMessage());
        assertEquals(List.of(), sleeps);
    }

    private DumHttpOaiPmhFetcher fetcher(int maxRetries) {
        return new DumHttpOaiPmhFetcher(HttpClient.newHttpClient(), new DumHttpRetry(sleeps::add, maxRetries));
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void respond(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
