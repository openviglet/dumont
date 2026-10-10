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

package com.viglet.dumont.connector.commons.spi.structured.jsonapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** The JDK client behind a JSON API source (roadmap D92), against an in-process server. */
class DumHttpJsonFetcherTest {

    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            method.set(exchange.getRequestMethod());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"results\": []}");
        });
        server.createContext("/fail", exchange -> respond(exchange, 503, "down"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void getsWithTheManifestHeaders() throws IOException {
        String response = new DumHttpJsonFetcher().fetch(new DumJsonApiRequest("GET", url("/ok"),
                Map.of("Authorization", "Bearer x"), null));

        assertEquals("{\"results\": []}", response);
        assertEquals("GET", method.get());
        assertEquals("Bearer x", auth.get());
    }

    @Test
    void postsAJsonBody() throws IOException {
        new DumHttpJsonFetcher().fetch(new DumJsonApiRequest("POST", url("/ok"), Map.of(), "{\"p\":1}"));

        assertEquals("POST", method.get());
        assertEquals("{\"p\":1}", body.get());
        assertEquals("application/json", contentType.get());
    }

    @Test
    void aNon2xxStatusFailsWithoutLeakingTheQuery() {
        IOException e = assertThrows(IOException.class, () -> new DumHttpJsonFetcher()
                .fetch(new DumJsonApiRequest("GET", url("/fail?app_key=secret"), Map.of(), null)));

        assertTrue(e.getMessage().contains("HTTP 503"));
        assertFalse(e.getMessage().contains("secret"), "the API key in the query is not logged");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String text)
            throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
