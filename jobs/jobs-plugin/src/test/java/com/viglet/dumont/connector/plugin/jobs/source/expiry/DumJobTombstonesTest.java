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

package com.viglet.dumont.connector.plugin.jobs.source.expiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;
import com.viglet.dumont.connector.plugin.jobs.source.FakeSite;
import com.viglet.dumont.connector.plugin.jobs.source.InMemoryTombstoneRepository;
import com.viglet.dumont.connector.plugin.jobs.source.MutableClock;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobHttpFetcher;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobRequest;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.occupation.DumJobOccupationEnricher;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;

/** The tombstone body, queue, retry and secrecy (trabalharei TRA24). */
class DumJobTombstonesTest {

    static final String TOKEN = "tok-3f9a1c77e2b04d51";
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private final InMemoryTombstoneRepository pending = new InMemoryTombstoneRepository();
    private final FakeSite site = new FakeSite();
    private final MutableClock clock = new MutableClock(NOW);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureEveryLog() {
        logs = new ListAppender<>();
        logs.start();
        root().addAppender(logs);
    }

    @AfterEach
    void release() {
        root().detachAppender(logs);
    }

    private static Logger root() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private DumJobTombstones tombstones(String url, String token) {
        return new DumJobTombstones(url, token, pending.repository, site, DumJobLocationEnricher.standard(),
                DumJobOccupationEnricher.dictionaryOnly(), clock);
    }

    static Map<String, Object> motorista() {
        Map<String, Object> posting = new LinkedHashMap<>();
        posting.put("id", "adzuna-77");
        posting.put("title", "Motorista Carreteiro");
        posting.put("city", "campinas");
        posting.put("state", "São Paulo");
        posting.put("applyUrl", "https://x/77");
        return posting;
    }

    @Test
    void theBodyIsTheExactJsonWithTheCanonicalPlaceAndOccupation() {
        DumJobTombstones tombstones = tombstones("https://trabalharei.example/", TOKEN);

        assertThat(tombstones.body("job-0a1b", motorista(), NOW)).isEqualTo("{\"id\":\"job-0a1b\","
                + "\"title\":\"Motorista Carreteiro\",\"city\":\"Campinas\",\"state\":\"SP\","
                + "\"occupation\":\"motoristas-de-veiculos-de-cargas-em-geral\","
                + "\"expiredAt\":\"2026-10-10T12:00:00Z\"}");

        // Absent fields are left out, not sent empty.
        Map<String, Object> bare = new LinkedHashMap<>();
        bare.put("title", "Vaga \"especial\"");
        assertThat(tombstones.body("bne-1", bare, NOW))
                .isEqualTo("{\"id\":\"bne-1\",\"title\":\"Vaga \\\"especial\\\"\",\"expiredAt\":\"2026-10-10T12:00:00Z\"}");
        assertThat(tombstones.body("bne-2", Map.of(), null)).isEqualTo("{\"id\":\"bne-2\"}");
    }

    @Test
    void aQueuedTombstoneIsPostedOnceWithTheBearerTokenThenForgotten() {
        DumJobTombstones tombstones = tombstones("https://trabalharei.example/", TOKEN);
        tombstones.queue("adzuna-77", motorista(), NOW);
        // Queued twice before a flush: one row, one POST.
        tombstones.queue("adzuna-77", motorista(), NOW);

        assertThat(tombstones.flush("adzuna-br")).isEqualTo(new DumJobTombstones.Flush(1, 0));
        DumJobRequest post = site.requests.getFirst();
        assertThat(post.method()).isEqualTo("POST");
        assertThat(post.uri()).hasToString("https://trabalharei.example/api/internal/tombstones/");
        assertThat(post.headers()).containsExactly(Map.entry("Authorization", "Bearer " + TOKEN));
        assertThat(post.secrets()).containsExactly(TOKEN);
        assertThat(post.body()).startsWith("{\"id\":\"adzuna-77\",\"title\":\"Motorista Carreteiro\"");
        assertThat(pending.rows).isEmpty();

        assertThat(tombstones.flush("adzuna-br")).isEqualTo(DumJobTombstones.Flush.NONE);
        assertThat(site.requests).hasSize(1);
    }

    @Test
    void aFailedPostStaysPendingAndIsRetriedOnTheNextFlush() {
        DumJobTombstones tombstones = tombstones("https://trabalharei.example", TOKEN);
        tombstones.queue("adzuna-77", motorista(), NOW);
        site.failure = new DumJobFetchException("HTTP 503 from POST https://trabalharei.example/api/internal/tombstones/");

        assertThat(tombstones.flush("adzuna-br")).isEqualTo(new DumJobTombstones.Flush(0, 1));
        assertThat(pending.find("adzuna-77")).get().satisfies(row -> {
            assertThat(row.getAttempts()).isEqualTo(1);
            assertThat(row.getLastError()).contains("HTTP 503");
        });

        site.failure = null;
        assertThat(tombstones.flush("adzuna-br")).isEqualTo(new DumJobTombstones.Flush(1, 0));
        assertThat(site.bodies()).hasSize(2).allMatch(body -> body.equals(site.bodies().getFirst()));
        assertThat(pending.rows).isEmpty();
    }

    @Test
    void theTokenNeverReachesALogLineOrAStoredError() {
        DumJobTombstones tombstones = tombstones("https://trabalharei.example", TOKEN);
        tombstones.queue("adzuna-77", motorista(), NOW);
        // A failure whose message quotes the token (a proxy echoing the header, say).
        site.failure = new IllegalStateException("rejected header Authorization: Bearer " + TOKEN);

        tombstones.flush("adzuna-br");

        assertThat(logs.list).isNotEmpty();
        for (ILoggingEvent event : logs.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(TOKEN);
            for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
                assertThat(t.getMessage()).doesNotContain(TOKEN);
            }
        }
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(line -> line.contains("Bearer " + DumJobHttpFetcher.MASK));
        assertThat(pending.find("adzuna-77").orElseThrow().getLastError()).doesNotContain(TOKEN);
    }

    @Test
    void aBlankUrlOrTokenQueuesNothingAndSaysSoOncePerRun() {
        for (String[] settings : new String[][] { { "", TOKEN }, { "https://trabalharei.example", " " },
                { null, null } }) {
            logs.list.clear();
            DumJobTombstones off = tombstones(settings[0], settings[1]);
            assertThat(off.enabled()).isFalse();
            off.queue("adzuna-77", motorista(), NOW);
            off.cancel(List.of("adzuna-77"));

            assertThat(off.flush("adzuna-br")).isEqualTo(DumJobTombstones.Flush.NONE);
            assertThat(pending.rows).isEmpty();
            assertThat(site.requests).isEmpty();
            assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                    .filteredOn(line -> line.contains("tombstones off")).containsExactly(
                            "Job source 'adzuna-br': tombstones off — dumont.jobs.tombstone.url or "
                                    + "dumont.jobs.tombstone.token is blank");
        }
    }

    @Test
    void aDocumentPublishedAgainHasItsPendingTombstoneCancelled() {
        DumJobTombstones tombstones = tombstones("https://trabalharei.example", TOKEN);
        tombstones.queue("adzuna-77", motorista(), NOW);
        tombstones.queue("adzuna-78", motorista(), NOW);

        tombstones.cancel(List.of("adzuna-77", "job-unrelated"));

        assertThat(pending.rows).extracting(row -> row.getDocumentId()).containsExactly("adzuna-78");
    }

    @Test
    void overRealHttpTheSiteGetsTheHeaderAndBodyAndAFailureLogsNoToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> received = new ArrayList<>();
        int[] status = { 500 };
        server.createContext("/", exchange -> {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            exchange.getRequestBody().transferTo(body);
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                    + exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + exchange.getRequestHeaders().getFirst("Content-Type") + " "
                    + body.toString(StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status[0], -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            DumJobTombstones tombstones = new DumJobTombstones(base + "/", TOKEN, pending.repository,
                    new DumJobHttpFetcher(), DumJobLocationEnricher.standard(),
                    DumJobOccupationEnricher.dictionaryOnly(), clock);
            tombstones.queue("job-0a1b", motorista(), NOW);

            assertThat(tombstones.flush("adzuna-br")).isEqualTo(new DumJobTombstones.Flush(0, 1));
            status[0] = 201;
            assertThat(tombstones.flush("adzuna-br")).isEqualTo(new DumJobTombstones.Flush(1, 0));

            String expected = "POST /api/internal/tombstones/ Bearer " + TOKEN + " application/json "
                    + "{\"id\":\"job-0a1b\",\"title\":\"Motorista Carreteiro\",\"city\":\"Campinas\","
                    + "\"state\":\"SP\",\"occupation\":\"motoristas-de-veiculos-de-cargas-em-geral\","
                    + "\"expiredAt\":\"2026-10-10T12:00:00Z\"}";
            assertThat(received).containsExactly(expected, expected);
            assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(line -> line.contains("HTTP 500"))
                    .noneMatch(line -> line.contains(TOKEN));
        } finally {
            server.stop(0);
        }
    }
}
