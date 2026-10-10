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

package com.viglet.dumont.connector.plugin.jobs.source.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** {@link DumJobHttpFetcher} against an in-process JDK {@link HttpServer}; credentials never leak. */
class DumJobHttpFetcherTest {

    private final DumJobHttpFetcher fetcher = new DumJobHttpFetcher();
    private final AtomicReference<String> header = new AtomicReference<>();
    private HttpServer server;
    private String baseUrl;
    private int status = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/search", exchange -> {
            header.set(exchange.getRequestHeaders().getFirst("X-Test"));
            byte[] body = "{\"results\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsTheBodyAndSendsHeaders() {
        String body = fetcher.get(URI.create(baseUrl + "/search/1?app_key=secret"),
                Map.of("X-Test", "yes"));
        assertThat(body).contains("results");
        assertThat(header.get()).isEqualTo("yes");
    }

    @Test
    void aNon2xxAnswerThrowsWithoutTheQueryString() {
        status = 401;
        URI uri = URI.create(baseUrl + "/search/1?app_id=id&app_key=secret");
        assertThatThrownBy(() -> fetcher.get(uri, Map.of()))
                .isInstanceOf(DumJobFetchException.class).hasMessageContaining("HTTP 401")
                .hasMessageContaining("/search/1").hasMessageNotContaining("secret");
    }

    @Test
    void aTransportErrorThrowsWithoutTheQueryString() {
        URI uri = URI.create("http://localhost:1/search/1?app_key=secret");
        assertThatThrownBy(() -> fetcher.get(uri, null))
                .isInstanceOf(DumJobFetchException.class).hasMessageContaining("failed")
                .hasMessageNotContaining("secret");
    }

    @Test
    void redactedKeepsSchemeHostPortAndPathOnly() {
        assertThat(DumJobHttpFetcher.redacted(
                URI.create("https://user:pw@api.adzuna.com/v1/api/jobs/br/search/2?app_key=k")))
                .isEqualTo("https://api.adzuna.com/v1/api/jobs/br/search/2");
    }
}
