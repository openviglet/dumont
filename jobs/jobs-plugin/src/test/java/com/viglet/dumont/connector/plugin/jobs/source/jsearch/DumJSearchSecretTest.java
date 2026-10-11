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

package com.viglet.dumont.connector.plugin.jobs.source.jsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobFetchContext;
import com.viglet.dumont.connector.plugin.jobs.source.MutableClock;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobHttpFetcher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * The RapidAPI key travels as the {@code X-RapidAPI-Key} header: a real HTTP round trip through
 * {@link DumJobHttpFetcher} must send it, and never print it — in an exception message or in a log line
 * at any level, including the {@code toString} of the request.
 */
class DumJSearchSecretTest {

    private static final String KEY = DumJSearchAdapterTest.API_KEY;

    private final List<Integer> statuses = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final List<String> receivedKeys = new CopyOnWriteArrayList<>();
    private final List<String> receivedHosts = new CopyOnWriteArrayList<>();
    private final AtomicInteger served = new AtomicInteger();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final List<Logger> loggers = new ArrayList<>();
    private HttpServer server;
    private String apiUrl;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/search", exchange -> {
            receivedKeys.add(exchange.getRequestHeaders().getFirst("X-RapidAPI-Key"));
            receivedHosts.add(exchange.getRequestHeaders().getFirst("X-RapidAPI-Host"));
            int call = served.getAndIncrement();
            int status = call < statuses.size() ? statuses.get(call) : 200;
            String text = call < bodies.size() ? bodies.get(call)
                    : status == 200 ? JSearchFixtureFetcher.read(call == 0 ? "page-1" : "empty")
                            : "{\"message\":\"You are not subscribed to this API.\"}";
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        apiUrl = "http://localhost:" + server.getAddress().getPort() + "/search";

        appender.start();
        for (Class<?> type : List.of(DumJSearchAdapter.class, DumJobHttpFetcher.class)) {
            Logger logger = (Logger) LoggerFactory.getLogger(type);
            logger.setLevel(Level.TRACE);
            logger.addAppender(appender);
            loggers.add(logger);
        }
    }

    @AfterEach
    void stop() {
        server.stop(0);
        loggers.forEach(logger -> {
            logger.detachAppender(appender);
            logger.setLevel(null);
        });
    }

    private List<Map<String, Object>> fetch() {
        DumJobSource source = DumJSearchAdapterTest.source(null);
        source.setApiUrl(apiUrl);
        MutableClock clock = new MutableClock(Instant.parse("2026-10-10T12:00:00Z"));
        return new DumJSearchAdapter(new DumJobHttpFetcher())
                .fetch(new DumJobFetchContext(source, DumJobCallBudget.unlimited(clock)));
    }

    private List<String> logLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void assertNoKeyLogged() {
        assertThat(logLines()).allSatisfy(line -> assertThat(line).doesNotContain(KEY));
        assertThat(appender.list).allSatisfy(event -> {
            for (Object argument : event.getArgumentArray() == null ? new Object[0] : event.getArgumentArray()) {
                assertThat(String.valueOf(argument)).doesNotContain(KEY);
            }
        });
    }

    @Test
    void aSuccessfulRunSendsTheKeyAsAHeaderAndLogsItNowhere() {
        assertThat(fetch()).hasSize(4);
        assertThat(receivedKeys).containsExactly(KEY, KEY);
        assertThat(receivedHosts).containsOnly("jsearch.p.rapidapi.com");
        // The fetcher's debug line names the call, without the query string or the key.
        assertThat(logLines()).anySatisfy(line -> assertThat(line).startsWith("Fetched GET http://localhost:")
                .contains("/search ("));
        assertNoKeyLogged();
    }

    @Test
    void aRefusedFirstCallThrowsWithoutTheKeyAndIsNotRetried() {
        statuses.add(403);
        assertThatThrownBy(this::fetch).isInstanceOf(DumJobFetchException.class)
                .hasMessageContaining("HTTP 403").hasMessageContaining("/search")
                .hasMessageNotContaining(KEY);
        assertThat(served).hasValue(1);
        assertNoKeyLogged();
    }

    @Test
    void anErrorStatusEchoingTheKeyIsMasked() {
        statuses.add(200);
        bodies.add("{\"status\":\"ERROR\",\"error\":{\"message\":\"Invalid API key " + KEY + "\",\"code\":403}}");
        assertThatThrownBy(this::fetch).isInstanceOf(DumJobFetchException.class)
                .hasMessageContaining("status ERROR").hasMessageContaining("Invalid API key ***")
                .hasMessageNotContaining(KEY);
        assertNoKeyLogged();
    }

    @Test
    void aLaterFailureIsLoggedWithoutTheKeyAndNotRetried() {
        statuses.add(200);
        statuses.add(429);
        assertThat(fetch()).hasSize(4);
        assertThat(served).hasValue(2);
        assertThat(logLines()).anySatisfy(line -> assertThat(line).contains("HTTP 429").contains("not retried"));
        assertNoKeyLogged();
    }

    @Test
    void anUnreachableHostThrowsWithoutTheKey() {
        server.stop(0);
        assertThatThrownBy(this::fetch).isInstanceOf(DumJobFetchException.class)
                .hasMessageContaining("failed").hasMessageNotContaining(KEY);
        assertNoKeyLogged();
    }
}
