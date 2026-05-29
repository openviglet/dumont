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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFacet;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSIndex;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.state.DumEDSCrawlStateService;
import com.viglet.dumont.connector.plugin.eds.testsupport.EmbeddedHttpServer;
import com.viglet.turing.client.sn.job.TurSNJobItem;

class DumEDSPluginProcessIndexingTest {

    private static final String PATH_PF = "/pt-br/pf";
    private static final String PATH_PJ = "/pt-br/pj";
    private static final String FACET_AUDIENCE = "audience";

    private static final String INDEX_JSON = """
            {
              "total": 2, "offset": 0, "limit": 100,
              "columns": ["path","title"],
              "data": [
                { "path": "/pt-br/pf", "title": "Example PF" },
                { "path": "/pt-br/pj" }
              ],
              ":type": "sheet"
            }
            """;

    private static final String PF_HTML = """
            <html><head><title>Example PF</title></head>
            <body><main><p>Conta digital.</p></main></body></html>
            """;

    private static final String PJ_HTML = """
            <html><head>
              <title>Example PJ</title>
              <meta property="og:description" content="Solução para empresas">
            </head>
            <body><main><p>Maquininha e mais.</p></main></body></html>
            """;

    @Test
    void pipelineEnqueuesJobItemsWithDerivedFacetsAndStableChecksum() throws IOException {
        try (EmbeddedHttpServer server = new EmbeddedHttpServer()) {
            server.route("/query-index.json",
                    exchange -> EmbeddedHttpServer.respondJson(exchange, INDEX_JSON));
            server.route(PATH_PF,
                    exchange -> EmbeddedHttpServer.respondHtml(exchange, PF_HTML));
            server.route(PATH_PJ,
                    exchange -> EmbeddedHttpServer.respondHtml(exchange, PJ_HTML));

            RecordingContext context = new RecordingContext();
            DumEDSPluginProcess process = newProcess(context);
            DumEDSSource source = testSource(server.baseUrl());

            process.start(source);

            assertThat(context.itemsByPath()).containsOnlyKeys(PATH_PF, PATH_PJ);

            TurSNJobItem pf = context.itemsByPath().get(PATH_PF);
            assertThat(pf.getAttributes()).containsEntry(FACET_AUDIENCE, "pf");
            assertThat(pf.getAttributes()).containsEntry("locale", "pt-br");
            assertThat(pf.getAttributes()).containsEntry("source_apps", "EDS");
            assertThat(pf.getChecksum()).isNotBlank();

            TurSNJobItem pj = context.itemsByPath().get(PATH_PJ);
            assertThat(pj.getAttributes()).containsEntry(FACET_AUDIENCE, "pj");

            // Spec test case 6 — rerun produces identical checksums (idempotency).
            RecordingContext rerun = new RecordingContext();
            newProcess(rerun).start(source);
            assertThat(rerun.itemsByPath().get(PATH_PF).getChecksum())
                    .isEqualTo(pf.getChecksum());
            assertThat(rerun.itemsByPath().get(PATH_PJ).getChecksum())
                    .isEqualTo(pj.getChecksum());

            assertThat(context.finished()).isTrue();
        }
    }

    private static DumEDSPluginProcess newProcess(DumConnectorContext context) {
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
                noopCrawlStateService());
    }

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

    private static DumEDSSource testSource(String baseUrl) {
        DumEDSSource source = new DumEDSSource();
        source.setId("test-source");
        source.setTitle("example");
        source.setBaseUrl(baseUrl);
        source.setSitemapFallback(null);
        source.setFacetPathPattern("/{locale}/{audience}/{section}/**");
        source.setUserAgent("eds-indexing-test");
        source.setMaxConcurrent(2);
        source.setRequestsPerSecond(0);
        source.setFetchTimeoutMs(5000);
        source.getTurSNSites().add("example-pt-br");
        source.getIndexes().add(new DumEDSIndex("/query-index.json", source));
        source.getFacets().add(new DumEDSFacet("locale", 0, null, source));
        source.getFacets().add(new DumEDSFacet(FACET_AUDIENCE, 1, "pf,pj", source));
        source.getFacets().add(new DumEDSFacet("section", 2, null, source));
        return source;
    }

    private static final class RecordingContext implements DumConnectorContext {
        private final List<TurSNJobItem> items = new ArrayList<>();
        private boolean finished = false;

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            items.add(item.turSNJobItem());
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            this.finished = true;
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

        Map<String, TurSNJobItem> itemsByPath() {
            Map<String, TurSNJobItem> map = new HashMap<>();
            for (TurSNJobItem item : items) {
                Object path = item.getAttributes().get("path");
                if (path != null) {
                    map.put(path.toString(), item);
                }
            }
            return map;
        }

        boolean finished() {
            return finished;
        }
    }
}
