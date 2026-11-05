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

package com.viglet.dumont.connector.commons.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** The back-off every outbound fetcher shares (roadmap D315), against an in-process server. */
class DumHttpRetryTest {

    private HttpServer server;
    private final AtomicInteger limitedAnswers = new AtomicInteger();
    private final List<Long> sleeps = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Answers 429 with Retry-After: 2 as many times as limitedAnswers says, then 200.
        server.createContext("/api", exchange -> {
            if (limitedAnswers.getAndDecrement() > 0) {
                exchange.getResponseHeaders().add("Retry-After", "2");
                respond(exchange, 429, "slow down");
            } else {
                respond(exchange, 200, "ok");
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void retriesA429AfterWhatRetryAfterAsks() throws Exception {
        limitedAnswers.set(2);

        HttpResponse<String> response = new DumHttpRetry(sleeps::add, 3)
                .send(HttpClient.newHttpClient(), get(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("ok", response.body());
        assertEquals(List.of(2_000L, 2_000L), sleeps);
    }

    @Test
    void handsBackTheLastBackOffOnceRetriesAreSpent() throws Exception {
        limitedAnswers.set(10);

        HttpResponse<String> response = new DumHttpRetry(sleeps::add, 1)
                .send(HttpClient.newHttpClient(), get(), HttpResponse.BodyHandlers.ofString());

        assertEquals(429, response.statusCode());
        assertEquals(1, sleeps.size());
    }

    @Test
    void closesAStreamedBodyItDiscards() throws Exception {
        limitedAnswers.set(1);

        HttpResponse<InputStream> response = new DumHttpRetry(sleeps::add, 2)
                .send(HttpClient.newHttpClient(), get(), HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream body = response.body()) {
            assertEquals("ok", new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(List.of(2_000L), sleeps);
    }

    @Test
    void retryAfterIsCappedAndDefaultsWhenUnreadable() {
        assertEquals(Duration.ofSeconds(30), DumHttpRetry.retryAfter(Optional.of(" 30 ")));
        assertEquals(DumHttpRetry.MAX_WAIT, DumHttpRetry.retryAfter(Optional.of("86400")));
        assertEquals(DumHttpRetry.DEFAULT_WAIT,
                DumHttpRetry.retryAfter(Optional.of("Wed, 21 Oct 2026 07:28:00 GMT")));
        assertEquals(DumHttpRetry.DEFAULT_WAIT, DumHttpRetry.retryAfter(Optional.empty()));
    }

    @Test
    void onlyTooManyRequestsAndUnavailableAreBackOffs() {
        assertTrue(DumHttpRetry.isBackOff(429));
        assertTrue(DumHttpRetry.isBackOff(503));
        assertFalse(DumHttpRetry.isBackOff(500));
        assertFalse(DumHttpRetry.isBackOff(404));
    }

    @Test
    void redactDropsTheQuery() {
        assertEquals("https://x/oai?…", DumHttpRetry.redact("https://x/oai?resumptionToken=abc"));
        assertEquals("https://x/oai", DumHttpRetry.redact("https://x/oai"));
    }

    private HttpRequest get() {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api"))
                .GET().build();
    }

    private static void respond(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
