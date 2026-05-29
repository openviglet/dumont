/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.eds.testsupport.EmbeddedHttpServer;

class DumEDSHttpFetcherTest {

    private static final String PAGE_PATH = "/page";
    private static final String USER_AGENT = "test-ua";

    @Test
    void fetchesHtmlAndExposesLastModifiedHeader() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route(PAGE_PATH, exchange -> {
                exchange.getResponseHeaders().add("Last-Modified",
                        "Wed, 01 May 2026 12:00:00 GMT");
                EmbeddedHttpServer.respondHtml(exchange,
                        "<html><head><title>Hello</title></head><body>x</body></html>");
            });

            DumEDSHttpFetcher fetcher = new DumEDSHttpFetcher();

            DumEDSHttpFetcher.FetchResult result = fetcher.fetch(
                    URI.create(server.baseUrl() + PAGE_PATH),
                    fastSettings(),
                    new DumEDSRateLimiter(DumEDSRateLimiter.UNLIMITED));

            assertThat(result.document().title()).isEqualTo("Hello");
            assertThat(result.lastModified()).contains("Wed, 01 May 2026 12:00:00 GMT");
        }
    }

    @Test
    void retriesOn429ThenSucceeds() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route(PAGE_PATH, exchange -> {
                int call = calls.incrementAndGet();
                if (call < 2) {
                    EmbeddedHttpServer.respondStatus(exchange, 429, "slow down");
                } else {
                    EmbeddedHttpServer.respondHtml(exchange,
                            "<html><head><title>OK</title></head><body></body></html>");
                }
            });

            DumEDSHttpFetcher fetcher = new DumEDSHttpFetcher();

            DumEDSHttpFetcher.FetchResult result = fetcher.fetch(
                    URI.create(server.baseUrl() + PAGE_PATH),
                    new DumEDSHttpFetcher.Settings(USER_AGENT, 5000, 0d, 3),
                    new DumEDSRateLimiter(DumEDSRateLimiter.UNLIMITED));

            assertThat(result.document().title()).isEqualTo("OK");
            assertThat(calls.get()).isEqualTo(2);
        }
    }

    @Test
    void givesUpAfterMaxRetriesOn503() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route(PAGE_PATH, exchange -> {
                calls.incrementAndGet();
                EmbeddedHttpServer.respondStatus(exchange, 503, "down");
            });

            DumEDSHttpFetcher fetcher = new DumEDSHttpFetcher();

            assertThatThrownBy(() -> fetcher.fetch(
                    URI.create(server.baseUrl() + PAGE_PATH),
                    new DumEDSHttpFetcher.Settings(USER_AGENT, 5000, 0d, 1),
                    new DumEDSRateLimiter(DumEDSRateLimiter.UNLIMITED)))
                    .isInstanceOf(DumEDSHttpFetcher.FetchException.class)
                    .hasMessageContaining("503");

            // maxRetries=1 → 2 total attempts (initial + 1 retry)
            assertThat(calls.get()).isEqualTo(2);
        }
    }

    @Test
    void doesNotRetryOn404() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route(PAGE_PATH, exchange -> {
                calls.incrementAndGet();
                EmbeddedHttpServer.respondStatus(exchange, 404, "nope");
            });

            DumEDSHttpFetcher fetcher = new DumEDSHttpFetcher();

            assertThatThrownBy(() -> fetcher.fetch(
                    URI.create(server.baseUrl() + PAGE_PATH),
                    new DumEDSHttpFetcher.Settings(USER_AGENT, 5000, 0d, 3),
                    new DumEDSRateLimiter(DumEDSRateLimiter.UNLIMITED)))
                    .isInstanceOf(DumEDSHttpFetcher.NotFoundException.class)
                    .hasMessageContaining("404");

            assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Test
    void doesNotRetryOn410() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route(PAGE_PATH, exchange -> {
                calls.incrementAndGet();
                EmbeddedHttpServer.respondStatus(exchange, 410, "gone");
            });

            DumEDSHttpFetcher fetcher = new DumEDSHttpFetcher();

            assertThatThrownBy(() -> fetcher.fetch(
                    URI.create(server.baseUrl() + PAGE_PATH),
                    new DumEDSHttpFetcher.Settings(USER_AGENT, 5000, 0d, 3),
                    new DumEDSRateLimiter(DumEDSRateLimiter.UNLIMITED)))
                    .isInstanceOf(DumEDSHttpFetcher.NotFoundException.class)
                    .hasMessageContaining("410");

            assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Test
    void isRetryableMatchesSpecStatuses() {
        assertThat(DumEDSHttpFetcher.isRetryable(429)).isTrue();
        assertThat(DumEDSHttpFetcher.isRetryable(503)).isTrue();
        assertThat(DumEDSHttpFetcher.isRetryable(500)).isTrue();
        assertThat(DumEDSHttpFetcher.isRetryable(502)).isTrue();
        assertThat(DumEDSHttpFetcher.isRetryable(504)).isTrue();
        assertThat(DumEDSHttpFetcher.isRetryable(408)).isTrue();

        assertThat(DumEDSHttpFetcher.isRetryable(400)).isFalse();
        assertThat(DumEDSHttpFetcher.isRetryable(404)).isFalse();
        assertThat(DumEDSHttpFetcher.isRetryable(200)).isFalse();
    }

    private static DumEDSHttpFetcher.Settings fastSettings() {
        return new DumEDSHttpFetcher.Settings(USER_AGENT, 5000, 0d, 0);
    }
}
