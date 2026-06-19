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

package com.viglet.dumont.connector.plugin.eds;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSIndexClient;
import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSPathFilter;
import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSSheetParser;
import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSSitemapClient;
import com.viglet.dumont.connector.plugin.eds.enrichment.DumEDSFacetDeriver;
import com.viglet.dumont.connector.plugin.eds.enrichment.DumEDSPathPatternMatcher;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSBodyExtractor;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSDocumentMerger;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSMetadataExtractor;
import com.viglet.dumont.connector.plugin.eds.fetch.DumEDSHttpFetcher;
import com.viglet.dumont.connector.plugin.eds.job.DumEDSJobItemBuilder;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSCrawlState;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSIndex;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.state.DumEDSCrawlStateService;
import com.viglet.dumont.connector.plugin.eds.testsupport.EmbeddedHttpServer;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

class DumEDSPluginProcessIncrementalTest {

    private static final String INDEX_PATH = "/query-index.json";
    private static final String PAGE_A = "/page-a";
    private static final String PAGE_B = "/page-b";
    private static final String LAST_MOD_VALUE = "2026-05-01T12:00:00Z";
    private static final String HTML_A = "<html><body>A</body></html>";
    private static final String HTML_B = "<html><body>B</body></html>";

    private static final String INDEX_ONLY_A = """
            { "total": 1, "offset": 0, "limit": 100,
              "columns": ["path"],
              "data": [ { "path": "/page-a" } ],
              ":type": "sheet" }
            """;

    private static final String INDEX_BOTH = """
            { "total": 2, "offset": 0, "limit": 100,
              "columns": ["path"],
              "data": [ { "path": "/page-a" }, { "path": "/page-b" } ],
              ":type": "sheet" }
            """;

    private static final String INDEX_ONLY_B = """
            { "total": 1, "offset": 0, "limit": 100,
              "columns": ["path"],
              "data": [ { "path": "/page-b" } ],
              ":type": "sheet" }
            """;

    @Test
    void skipsFetchWhenLastModifiedUnchanged() throws IOException {
        AtomicInteger pageFetches = new AtomicInteger();
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route(INDEX_PATH,
                    exchange -> EmbeddedHttpServer.respondJson(exchange, indexWithLastModified()));
            server.route(PAGE_A, exchange -> {
                pageFetches.incrementAndGet();
                EmbeddedHttpServer.respondHtml(exchange, HTML_A);
            });
            server.route(PAGE_B, exchange -> {
                pageFetches.incrementAndGet();
                EmbeddedHttpServer.respondHtml(exchange, HTML_B);
            });

            RecordingContext context = new RecordingContext();
            // Pre-populate state so page-a is known with the same lastModified.
            InMemoryCrawlStateService state = new InMemoryCrawlStateService();
            DumEDSSource source = sourceFor(server.baseUrl());
            DumEDSCrawlState priorA = new DumEDSCrawlState();
            priorA.setDumEDSSource(source);
            priorA.setPath(PAGE_A);
            priorA.setLastModified(LAST_MOD_VALUE);
            priorA.setChecksum("prior-checksum");
            priorA.setLastSeenAt(Instant.parse("2026-01-01T00:00:00Z").minusSeconds(3600));
            state.seed(priorA);

            DumEDSPluginProcess process = newProcess(context, state);
            process.start(source);

            // Page A should be skipped (unchanged), only Page B fetched.
            assertThat(pageFetches.get()).isEqualTo(1);
            assertThat(context.creates()).hasSize(1);
            assertThat(context.creates().get(0).getAttributes())
                    .containsEntry("path", PAGE_B);
            assertThat(state.touched()).contains(PAGE_A);
        }
    }

    @Test
    void emitsDeleteWhenPathDisappearsFromIndex() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            // Index has only page-a now; page-b dropped.
            server.route(INDEX_PATH,
                    exchange -> EmbeddedHttpServer.respondJson(exchange, INDEX_ONLY_A));
            server.route(PAGE_A,
                    exchange -> EmbeddedHttpServer.respondHtml(exchange, HTML_A));

            RecordingContext context = new RecordingContext();
            InMemoryCrawlStateService state = new InMemoryCrawlStateService();
            DumEDSSource source = sourceFor(server.baseUrl());
            DumEDSCrawlState priorB = new DumEDSCrawlState();
            priorB.setDumEDSSource(source);
            priorB.setPath(PAGE_B);
            priorB.setLastModified(null);
            priorB.setChecksum("prior");
            state.seed(priorB);
            // Also seed page-a so we know cleanup discriminates correctly.
            DumEDSCrawlState priorA = new DumEDSCrawlState();
            priorA.setDumEDSSource(source);
            priorA.setPath(PAGE_A);
            state.seed(priorA);

            newProcess(context, state).start(source);

            assertThat(context.deletes()).hasSize(1);
            TurSNJobItem deleted = context.deletes().get(0);
            assertThat(deleted.getTurSNJobAction()).isEqualTo(TurSNJobAction.DELETE);
            assertThat(deleted.getAttributes())
                    .containsEntry("id", server.baseUrl() + PAGE_B);
            assertThat(state.deleted()).contains(PAGE_B);
            assertThat(state.deleted()).doesNotContain(PAGE_A);
        }
    }

    @Test
    void fetchReturning404OnKnownPathEmitsDeleteAndPurgesState() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            // page-a still discoverable, page-b discoverable too but returns 404
            server.route(INDEX_PATH,
                    exchange -> EmbeddedHttpServer.respondJson(exchange, INDEX_BOTH));
            server.route(PAGE_A,
                    exchange -> EmbeddedHttpServer.respondHtml(exchange, HTML_A));
            server.route(PAGE_B,
                    exchange -> EmbeddedHttpServer.respondStatus(exchange, 404, "gone"));

            RecordingContext context = new RecordingContext();
            InMemoryCrawlStateService state = new InMemoryCrawlStateService();
            DumEDSSource source = sourceFor(server.baseUrl());
            // page-b was indexed previously — 404 now should DELETE it.
            DumEDSCrawlState priorB = new DumEDSCrawlState();
            priorB.setDumEDSSource(source);
            priorB.setPath(PAGE_B);
            priorB.setChecksum("prior-checksum");
            state.seed(priorB);

            newProcess(context, state).start(source);

            assertThat(context.deletes()).hasSize(1);
            assertThat(context.deletes().get(0).getAttributes())
                    .containsEntry("id", server.baseUrl() + PAGE_B);
            assertThat(state.deleted()).contains(PAGE_B);
            assertThat(context.creates()).hasSize(1);
            assertThat(context.creates().get(0).getAttributes())
                    .containsEntry("path", PAGE_A);
        }
    }

    @Test
    void fetchReturning404OnUnknownPathDoesNothing() throws IOException {
        // Defensive: 404 on a path we've never seen is just noise — don't pollute the queue.
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route(INDEX_PATH,
                    exchange -> EmbeddedHttpServer.respondJson(exchange, INDEX_ONLY_B));
            server.route(PAGE_B,
                    exchange -> EmbeddedHttpServer.respondStatus(exchange, 404, "gone"));

            RecordingContext context = new RecordingContext();
            InMemoryCrawlStateService state = new InMemoryCrawlStateService();

            newProcess(context, state).start(sourceFor(server.baseUrl()));

            assertThat(context.deletes()).isEmpty();
            assertThat(context.creates()).isEmpty();
            assertThat(state.deleted()).isEmpty();
        }
    }

    @Test
    void firstCrawlFetchesEverythingAndPersistsState() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route(INDEX_PATH,
                    exchange -> EmbeddedHttpServer.respondJson(exchange, indexWithLastModified()));
            server.route(PAGE_A, exchange -> EmbeddedHttpServer.respondHtml(exchange, HTML_A));
            server.route(PAGE_B, exchange -> EmbeddedHttpServer.respondHtml(exchange, HTML_B));

            RecordingContext context = new RecordingContext();
            InMemoryCrawlStateService state = new InMemoryCrawlStateService();

            newProcess(context, state).start(sourceFor(server.baseUrl()));

            assertThat(context.creates()).hasSize(2);
            assertThat(state.persistedPaths()).contains(PAGE_A, PAGE_B);
        }
    }

    private static String indexWithLastModified() {
        return """
                { "total": 2, "offset": 0, "limit": 100,
                  "columns": ["path","lastModified"],
                  "data": [
                    { "path": "/page-a", "lastModified": "%s" },
                    { "path": "/page-b" }
                  ],
                  ":type": "sheet" }
                """.formatted(LAST_MOD_VALUE);
    }

    private static DumEDSPluginProcess newProcess(DumConnectorContext context,
            DumEDSCrawlStateService stateService) {
        DumEDSSheetParser parser = new DumEDSSheetParser();
        RestClient.Builder builder = RestClient.builder();
        DumEDSFacetDeriver deriver = new DumEDSFacetDeriver(new DumEDSPathPatternMatcher());
        return new DumEDSPluginProcess(
                new DumEDSIndexClient(parser, builder),
                new DumEDSSitemapClient(builder),
                new DumEDSPathFilter(),
                new DumEDSHttpFetcher(),
                new DumEDSMetadataExtractor(),
                new DumEDSBodyExtractor(),
                new DumEDSDocumentMerger(),
                new DumEDSJobItemBuilder(deriver),
                context,
                stateService);
    }

    private static DumEDSSource sourceFor(String baseUrl) {
        DumEDSSource source = new DumEDSSource();
        source.setId("test-source");
        source.setTitle("test");
        source.setBaseUrl(baseUrl);
        source.setSitemapFallback(null);
        source.setUserAgent("eds-incremental-test");
        source.setMaxConcurrent(2);
        source.setRequestsPerSecond(0);
        source.setFetchTimeoutMs(5000);
        source.getTurSNSites().add("test-site");
        source.getIndexes().add(new DumEDSIndex(INDEX_PATH, source));
        return source;
    }

    private static final class RecordingContext implements DumConnectorContext {
        private final List<TurSNJobItem> items = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            items.add(item.turSNJobItem());
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            // no-op
        }

        @Override
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source,
                String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider,
                List<String> dependenciesObjectIdList) {
            return List.of();
        }

        List<TurSNJobItem> creates() {
            return items.stream()
                    .filter(i -> i.getTurSNJobAction() == TurSNJobAction.CREATE).toList();
        }

        List<TurSNJobItem> deletes() {
            return items.stream()
                    .filter(i -> i.getTurSNJobAction() == TurSNJobAction.DELETE).toList();
        }
    }

    /** In-memory implementation of the service for fast deterministic tests. */
    private static final class InMemoryCrawlStateService extends DumEDSCrawlStateService {
        private final Map<String, DumEDSCrawlState> store = new LinkedHashMap<>();
        private final List<String> touched = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();

        InMemoryCrawlStateService() {
            super(null);
        }

        void seed(DumEDSCrawlState state) {
            store.put(state.getPath(), state);
        }

        @Override
        public Map<String, DumEDSCrawlState> loadByPath(DumEDSSource source) {
            return new HashMap<>(store);
        }

        @Override
        public DumEDSCrawlState upsert(DumEDSSource source, String path, String lastModified,
                String checksum, boolean indexed) {
            DumEDSCrawlState state = store.computeIfAbsent(path, p -> {
                DumEDSCrawlState s = new DumEDSCrawlState();
                s.setDumEDSSource(source);
                s.setPath(p);
                return s;
            });
            state.setLastModified(lastModified);
            state.setChecksum(checksum);
            state.setLastSeenAt(Instant.parse("2026-01-01T00:00:00Z"));
            if (indexed) {
                state.setLastIndexedAt(Instant.parse("2026-01-01T00:00:00Z"));
            }
            return state;
        }

        @Override
        public DumEDSCrawlState touch(DumEDSCrawlState prior) {
            touched.add(prior.getPath());
            prior.setLastSeenAt(Instant.parse("2026-01-01T00:00:00Z"));
            return prior;
        }

        @Override
        public int delete(DumEDSSource source, String path) {
            deleted.add(path);
            return store.remove(path) == null ? 0 : 1;
        }

        List<String> touched() {
            return touched;
        }

        List<String> deleted() {
            return deleted;
        }

        List<String> persistedPaths() {
            return new ArrayList<>(store.keySet());
        }
    }
}
