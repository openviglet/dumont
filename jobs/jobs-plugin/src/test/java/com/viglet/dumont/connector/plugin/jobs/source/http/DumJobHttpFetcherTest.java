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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** {@link DumJobHttpFetcher} against an in-process JDK {@link HttpServer}; credentials never leak. */
class DumJobHttpFetcherTest {

    private final DumJobHttpFetcher fetcher = new DumJobHttpFetcher();
    private final AtomicReference<String> header = new AtomicReference<>();
    private final AtomicReference<String> received = new AtomicReference<>();
    private HttpServer server;
    private String baseUrl;
    private int status = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/search", exchange -> {
            header.set(exchange.getRequestHeaders().getFirst("X-Test"));
            received.set(exchange.getRequestMethod() + " "
                    + exchange.getRequestHeaders().getFirst("Content-Type") + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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

    @Test
    void postsAJsonBody() {
        String body = fetcher.send(DumJobRequest.post(URI.create(baseUrl + "/search/key-123"),
                Map.of("X-Test", "post"), "{\"keywords\":\"vagas\"}", Set.of("key-123")));
        assertThat(body).contains("results");
        assertThat(header.get()).isEqualTo("post");
        assertThat(received.get()).isEqualTo("POST application/json {\"keywords\":\"vagas\"}");
    }

    @Test
    void aSecretInThePathIsMaskedInANon2xxError() {
        status = 403;
        DumJobRequest request = DumJobRequest.post(URI.create(baseUrl + "/search/s3cr3t-key"),
                Map.of(), "{}", Set.of("s3cr3t-key"));
        assertThatThrownBy(() -> fetcher.send(request))
                .isInstanceOf(DumJobFetchException.class).hasMessageContaining("HTTP 403")
                .hasMessageContaining("POST " + baseUrl + "/search/***")
                .hasMessageNotContaining("s3cr3t-key");
    }

    @Test
    void aSecretInThePathIsMaskedInATransportError() {
        DumJobRequest request = new DumJobRequest("GET",
                URI.create("http://localhost:1/search/s3cr3t-key?page=1"), null, null,
                Set.of("s3cr3t-key"));
        assertThatThrownBy(() -> fetcher.send(request))
                .isInstanceOf(DumJobFetchException.class).hasMessageContaining("failed")
                .hasMessageContaining("/search/***").hasMessageNotContaining("s3cr3t-key");
    }

    @Test
    void redactedMasksPathSecretsRawOrEncoded() {
        assertThat(DumJobHttpFetcher.redacted(
                URI.create("https://jooble.org/api/abc-123?keywords=x"), Set.of("abc-123")))
                .isEqualTo("https://jooble.org/api/***");
        assertThat(DumJobHttpFetcher.redacted(
                URI.create("https://jooble.org/api/a%20b%2Fc"), Set.of("a b/c")))
                .isEqualTo("https://jooble.org/api/***");
        assertThat(DumJobHttpFetcher.scrub("failed for /api/abc-123", List.of("abc-123", " ")))
                .isEqualTo("failed for /api/***");
    }

    @Test
    void onlyGetAndPostAreRequests() {
        assertThatThrownBy(() -> new DumJobRequest("DELETE", URI.create(baseUrl), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // A GET-only fetcher (the interface default) refuses a POST instead of sending a GET.
        DumJobFetcher getOnly = (uri, headers) -> "{}";
        assertThat(getOnly.send(DumJobRequest.get(URI.create(baseUrl), Map.of()))).isEqualTo("{}");
        assertThatThrownBy(() -> getOnly.send(
                DumJobRequest.post(URI.create(baseUrl), Map.of(), "{}", Set.of())))
                .isInstanceOf(DumJobFetchException.class).hasMessageContaining("POST");
    }
}
