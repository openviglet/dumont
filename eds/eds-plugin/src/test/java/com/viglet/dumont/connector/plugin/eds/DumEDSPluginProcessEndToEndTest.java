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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.eds.commons.domain.DumEDSFetchedPage;
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
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSRemoveSelector;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.state.DumEDSCrawlStateService;
import com.viglet.dumont.connector.plugin.eds.testsupport.EmbeddedHttpServer;

/**
 * End-to-end pipeline test: discovery (via embedded query-index.json) → exclude filter →
 * HTTP fetch → metadata + body extraction → JSON ∪ HTML merge. Covers spec test cases:
 *
 * <ul>
 *   <li>Test case 3: page with no title in index is indexed with title filled via {@code og:title}.</li>
 *   <li>Test case 5: body extracted from {@code <main>} does not contain the "Baixe o App" CTA
 *       repeated in nav/footer.</li>
 * </ul>
 */
class DumEDSPluginProcessEndToEndTest {

    private static final String QUERY_INDEX_JSON = """
            {
              "total": 2, "offset": 0, "limit": 100,
              "columns": ["path","title"],
              "data": [
                { "path": "/pt-br/pf", "title": "Example PF" },
                { "path": "/pt-br/pf/conta-digital" }
              ],
              ":type": "sheet"
            }
            """;

    private static final String PF_HTML = """
            <html>
              <head><title>Example PF</title></head>
              <body>
                <header><nav><a href="/baixe-o-app">Baixe o App</a></nav></header>
                <main>
                  <h1>Welcome to Example</h1>
                  <p>Digital account, pix, investments and credit card.</p>
                </main>
                <footer><a href="/baixe-o-app">Baixe o App</a></footer>
              </body>
            </html>
            """;

    private static final String CONTA_HTML = """
            <html>
              <head>
                <title>Conta Digital | Example</title>
                <meta property="og:title" content="Conta Digital">
                <meta property="og:description" content="Your example account">
              </head>
              <body>
                <main>
                  <p>Abra sua conta sem tarifa.</p>
                </main>
              </body>
            </html>
            """;

    @Test
    void siteFlowFillsMissingTitleFromOgTitleAndStripsCtaFromBody() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route("/query-index.json",
                    exchange -> EmbeddedHttpServer.respondJson(exchange, QUERY_INDEX_JSON));
            server.route("/pt-br/pf",
                    exchange -> EmbeddedHttpServer.respondHtml(exchange, PF_HTML));
            server.route("/pt-br/pf/conta-digital",
                    exchange -> EmbeddedHttpServer.respondHtml(exchange, CONTA_HTML));

            DumEDSPluginProcess process = newProcess();
            DumEDSSource source = sourceFor(server.baseUrl());

            List<DumEDSFetchedPage> fetched = process.start(source);

            assertThat(fetched).hasSize(2);
            Map<String, DumEDSFetchedPage> byPath = byPath(fetched);

            DumEDSFetchedPage pf = byPath.get("/pt-br/pf");
            assertThat(pf.body()).contains("Welcome to Example");
            assertThat(pf.body()).contains("Digital account, pix, investments and credit card");
            assertThat(pf.body()).doesNotContain("Baixe o App");

            DumEDSFetchedPage conta = byPath.get("/pt-br/pf/conta-digital");
            assertThat(conta.metadata())
                    .containsEntry("title", "Conta Digital")
                    .containsEntry("description", "Your example account");
            assertThat(conta.body()).contains("Abra sua conta sem tarifa");
        }
    }

    private static Map<String, DumEDSFetchedPage> byPath(List<DumEDSFetchedPage> fetched) {
        return fetched.stream()
                .collect(java.util.stream.Collectors.toMap(DumEDSFetchedPage::path,
                        page -> page, (a, b) -> a));
    }

    private static DumEDSPluginProcess newProcess() {
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
                noopConnectorContext(),
                noopCrawlStateService());
    }

    /**
     * The end-to-end test deliberately bypasses real state storage — it
     * verifies the fetch→extract→merge pipeline, not Phase 5 incremental logic.
     * Returning empty prior state means every page is fetched fresh.
     */
    private static DumEDSCrawlStateService noopCrawlStateService() {
        return new DumEDSCrawlStateService(null) {
            @Override
            public java.util.Map<String, DumEDSCrawlState> loadByPath(DumEDSSource source) {
                return java.util.Map.of();
            }

            @Override
            public boolean isUnchanged(DumEDSCrawlState prior, String currentLastModified) {
                return false;
            }

            @Override
            public DumEDSCrawlState upsert(DumEDSSource source, String path, String lastModified,
                    String checksum, boolean indexed) {
                return null;
            }

            @Override
            public DumEDSCrawlState touch(DumEDSCrawlState prior) {
                return prior;
            }

            @Override
            public int delete(DumEDSSource source, String path) {
                return 0;
            }
        };
    }

    private static DumConnectorContext noopConnectorContext() {
        return new DumConnectorContext() {
            @Override
            public boolean addJobItem(
                    com.viglet.dumont.connector.commons.domain.DumJobItemWithSession item) {
                return true;
            }

            @Override
            public void finishIndexing(
                    com.viglet.dumont.connector.commons.DumConnectorSession session,
                    boolean standalone) {
                // no-op
            }

            @Override
            public List<com.viglet.dumont.connector.commons.domain.DumConnectorIndexing>
                    getIndexingItem(String objectId, String source, String provider) {
                return List.of();
            }

            @Override
            public List<String> getObjectIdByDependency(String source, String provider,
                    List<String> dependenciesObjectIdList) {
                return List.of();
            }
        };
    }

    private static DumEDSSource sourceFor(String baseUrl) {
        DumEDSSource source = new DumEDSSource();
        source.setId("test-source");
        source.setTitle("test");
        source.setBaseUrl(baseUrl);
        source.setSitemapFallback(null);
        source.setUserAgent("eds-e2e-test");
        source.setBodySelector("main");
        source.setFetchTimeoutMs(5000);
        source.setRequestsPerSecond(0);
        source.setMaxConcurrent(4);
        source.getIndexes().add(new DumEDSIndex("/query-index.json", source));
        // No exclude patterns in this scenario; the "Baixe o App" filtering is
        // demonstrated via removeSelectors below.
        source.getExcludePatterns().clear();
        for (String sel : List.of("nav", "footer", "aside", "header", "[role=navigation]")) {
            source.getRemoveSelectors().add(new DumEDSRemoveSelector(sel, source));
        }
        return source;
    }
}
