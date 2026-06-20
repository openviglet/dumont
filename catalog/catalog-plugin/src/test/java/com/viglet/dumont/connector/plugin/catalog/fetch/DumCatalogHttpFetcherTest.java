/*
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

package com.viglet.dumont.connector.plugin.catalog.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;

/**
 * Exercises {@link DumCatalogHttpFetcher} against an in-process JDK {@link HttpServer}: success,
 * optional auth header, blank URL, non-2xx response and transport failure.
 */
class DumCatalogHttpFetcherTest {

    private final DumCatalogHttpFetcher fetcher = new DumCatalogHttpFetcher();
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> seenAuthHeader = new AtomicReference<>();
    private int statusToReturn = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/catalog", exchange -> {
            seenAuthHeader.set(exchange.getRequestHeaders().getFirst("X-Api-Key"));
            byte[] body = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusToReturn, body.length);
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

    private DumCatalogSource source(String url) {
        return DumCatalogSource.builder().name("cat").catalogUrl(url).build();
    }

    @Test
    void fetchReturnsBodyOnSuccess() {
        assertThat(fetcher.fetch(source(baseUrl + "/catalog"))).contains("items");
    }

    @Test
    void fetchSendsConfiguredAuthHeader() {
        DumCatalogSource source = DumCatalogSource.builder().name("cat")
                .catalogUrl(baseUrl + "/catalog").authHeaderName("X-Api-Key")
                .authHeaderValue("secret-token").build();

        fetcher.fetch(source);

        assertThat(seenAuthHeader.get()).isEqualTo("secret-token");
    }

    @Test
    void fetchThrowsWhenUrlBlank() {
        DumCatalogSource source = source("  ");
        assertThatThrownBy(() -> fetcher.fetch(source))
                .isInstanceOf(DumCatalogFetchException.class).hasMessageContaining("no catalogUrl");
    }

    @Test
    void fetchThrowsOnNonSuccessStatus() {
        statusToReturn = 500;
        DumCatalogSource source = source(baseUrl + "/catalog");
        assertThatThrownBy(() -> fetcher.fetch(source))
                .isInstanceOf(DumCatalogFetchException.class).hasMessageContaining("HTTP 500");
    }

    @Test
    void fetchThrowsOnTransportError() {
        DumCatalogSource source = source("http://localhost:1/catalog");
        assertThatThrownBy(() -> fetcher.fetch(source))
                .isInstanceOf(DumCatalogFetchException.class).hasMessageContaining("failed");
    }
}
