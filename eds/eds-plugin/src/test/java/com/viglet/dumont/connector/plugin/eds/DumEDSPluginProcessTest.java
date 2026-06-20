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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.eds.commons.domain.DumEDSFetchedPage;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.eds.commons.domain.DumEDSDiscoveredPage;
import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSIndexClient;
import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSPathFilter;
import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSSitemapClient;
import com.viglet.dumont.connector.plugin.eds.enrichment.DumEDSFacetDeriver;
import com.viglet.dumont.connector.plugin.eds.enrichment.DumEDSPathPatternMatcher;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSBodyExtractor;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSDocumentMerger;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSMetadataExtractor;
import com.viglet.dumont.connector.plugin.eds.fetch.DumEDSHttpFetcher;
import com.viglet.dumont.connector.plugin.eds.job.DumEDSJobItemBuilder;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSExcludePattern;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSIndex;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.state.DumEDSCrawlStateService;

@ExtendWith(MockitoExtension.class)
class DumEDSPluginProcessTest {

    private static final String BASE_URL = "https://example.invalid";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_TITLE = "title";
    private static final String PATH_PF = "/pt-br/pf";

    @Mock
    private DumEDSIndexClient indexClient;

    @Mock
    private DumEDSSitemapClient sitemapClient;

    @Mock
    private DumEDSHttpFetcher httpFetcher;

    @Mock
    private DumConnectorContext connectorContext;

    @Mock
    private DumEDSCrawlStateService crawlStateService;

    private DumEDSPluginProcess process;

    @BeforeEach
    void setUp() {
        DumEDSFacetDeriver deriver = new DumEDSFacetDeriver(new DumEDSPathPatternMatcher());
        process = new DumEDSPluginProcess(indexClient, sitemapClient, new DumEDSPathFilter(),
                httpFetcher, new DumEDSMetadataExtractor(), new DumEDSBodyExtractor(),
                new DumEDSDocumentMerger(), new DumEDSJobItemBuilder(deriver), connectorContext,
                crawlStateService);
    }

    @Test
    void aggregatesPathsAcrossIndexesAndAppliesExcludePatterns() {
        DumEDSSource source = sourceWith(
                List.of("/pf-index.json", "/pj-index.json"),
                List.of("/footer", "/**/baixe-o-app", "/**/pagina-de-erro"));

        when(indexClient.fetchAllRows(eq(BASE_URL), eq("/pf-index.json"), any()))
                .thenReturn(List.of(
                        Map.of(FIELD_PATH, PATH_PF),
                        Map.of(FIELD_PATH, "/pt-br/pf/conta-digital"),
                        Map.of(FIELD_PATH, "/footer", FIELD_TITLE, "Footer"),
                        Map.of(FIELD_PATH, "/pt-br/pf/baixe-o-app"),
                        Map.of(FIELD_PATH, "/pt-br/pf/pagina-de-erro")));
        when(indexClient.fetchAllRows(eq(BASE_URL), eq("/pj-index.json"), any()))
                .thenReturn(List.of(
                        Map.of(FIELD_PATH, "/pt-br/pj"),
                        Map.of(FIELD_PATH, "/pt-br/pj/baixe-o-app")));
        when(sitemapClient.fetchPaths(any(), any(), any())).thenReturn(Set.of());

        List<DumEDSDiscoveredPage> kept = process.discoverAndFilter(source);

        List<String> paths = kept.stream().map(DumEDSDiscoveredPage::path).toList();
        assertThat(paths).containsExactly(
                PATH_PF,
                "/pt-br/pf/conta-digital",
                "/pt-br/pj");
    }

    @Test
    void mergesSitemapExtrasWithIndexOrigin() {
        DumEDSSource source = sourceWith(List.of("/query-index.json"), List.of());

        when(indexClient.fetchAllRows(any(), any(), any())).thenReturn(List.of(
                Map.of(FIELD_PATH, PATH_PF, FIELD_TITLE, "PF")));
        when(sitemapClient.fetchPaths(any(), any(), any())).thenReturn(Set.of(
                PATH_PF,
                "/pt-br/orphan-from-sitemap"));

        List<DumEDSDiscoveredPage> kept = process.discoverAndFilter(source);

        Map<String, DumEDSDiscoveredPage> byPath = new LinkedHashMap<>();
        kept.forEach(p -> byPath.put(p.path(), p));
        assertThat(byPath.get(PATH_PF).origin()).isEqualTo(DumEDSDiscoveredPage.ORIGIN_INDEX);
        assertThat(byPath.get("/pt-br/orphan-from-sitemap").origin())
                .isEqualTo(DumEDSDiscoveredPage.ORIGIN_SITEMAP);
    }

    @Test
    void preservesIndexMetadataOnDiscoveredPages() {
        DumEDSSource source = sourceWith(List.of("/query-index.json"), List.of());
        when(indexClient.fetchAllRows(any(), any(), any())).thenReturn(List.of(
                Map.of(FIELD_PATH, "/pt-br/sala-de-imprensa", FIELD_TITLE, "Sala de Imprensa",
                        "image", "https://example.invalid/img.webp")));
        when(sitemapClient.fetchPaths(any(), any(), any())).thenReturn(Set.of());

        List<DumEDSDiscoveredPage> kept = process.discoverAndFilter(source);

        assertThat(kept).singleElement().satisfies(page -> {
            assertThat(page.path()).isEqualTo("/pt-br/sala-de-imprensa");
            assertThat(page.metadata()).containsEntry(FIELD_TITLE, "Sala de Imprensa");
            assertThat(page.metadata()).containsKey("image");
        });
    }

    @Test
    void emptySourceReturnsEmptyList() {
        DumEDSSource source = sourceWith(List.of(), List.of());

        List<DumEDSDiscoveredPage> kept = process.discoverAndFilter(source);

        assertThat(kept).isEmpty();
    }

    @Test
    void startFetchesChangedPagesAndEnqueuesJobItems() {
        DumEDSSource source = sourceWith(List.of("/query-index.json"), List.of());
        source.setLocale(Locale.US);  // short-circuits per-page locale resolution
        source.setBodySelector("main");
        when(indexClient.fetchAllRows(any(), any(), any())).thenReturn(List.of(
                Map.of(FIELD_PATH, PATH_PF, "lastModified", "2024-01-01")));
        when(sitemapClient.fetchPaths(any(), any(), any())).thenReturn(Set.of());
        when(crawlStateService.loadByPath(source)).thenReturn(Map.of());
        when(crawlStateService.isUnchanged(any(), any())).thenReturn(false);
        when(httpFetcher.fetch(any(), any(), any())).thenReturn(new DumEDSHttpFetcher.FetchResult(
                Jsoup.parse("<html><head><title>PF</title></head><body><main>Hello PF</main></body></html>",
                        BASE_URL),
                Optional.of("2024-01-01")));
        when(connectorContext.addJobItem(any())).thenReturn(true);

        List<DumEDSFetchedPage> fetched = process.start(source);

        assertThat(fetched).singleElement()
                .satisfies(page -> assertThat(page.path()).isEqualTo(PATH_PF));
        verify(connectorContext, atLeastOnce()).addJobItem(any());
        verify(crawlStateService).upsert(eq(source), eq(PATH_PF), any(), any(), eq(true));
        verify(connectorContext).finishIndexing(any(), eq(false));
    }

    @Test
    void startWithNoDiscoveryReturnsEmptyAndFinishes() {
        DumEDSSource source = sourceWith(List.of(), List.of());
        when(crawlStateService.loadByPath(source)).thenReturn(Map.of());

        assertThat(process.start(source)).isEmpty();

        verify(connectorContext).finishIndexing(any(), eq(false));
    }

    @Test
    void reindexClearsCrawlStateBeforeRunning() {
        DumEDSSource source = sourceWith(List.of(), List.of());
        when(crawlStateService.deleteAll(source)).thenReturn(3);
        when(crawlStateService.loadByPath(source)).thenReturn(Map.of());

        assertThat(process.reindex(source)).isEmpty();

        verify(crawlStateService).deleteAll(source);
    }

    @Test
    void dryScanReportsDiscoveredPathsWithoutFetching() {
        DumEDSSource source = sourceWith(List.of("/query-index.json"), List.of());
        when(indexClient.fetchAllRows(any(), any(), any())).thenReturn(List.of(
                Map.of(FIELD_PATH, PATH_PF), Map.of(FIELD_PATH, "/pt-br/pj")));
        when(sitemapClient.fetchPaths(any(), any(), any())).thenReturn(Set.of());

        DumEDSPluginProcess.DryScanResult result = process.dryScan(source);

        assertThat(result.discovered()).isEqualTo(2);
        assertThat(result.paths()).containsExactly(PATH_PF, "/pt-br/pj");
    }

    private static DumEDSSource sourceWith(List<String> indexPaths, List<String> excludePatterns) {
        DumEDSSource source = new DumEDSSource();
        source.setId("test-source");
        source.setTitle("test");
        source.setBaseUrl(BASE_URL);
        source.setSitemapFallback("/sitemap.xml");
        source.setUserAgent("test-ua");
        source.getIndexes().addAll(indexPaths.stream()
                .map(p -> new DumEDSIndex(p, source)).toList());
        source.getExcludePatterns().addAll(excludePatterns.stream()
                .map(p -> new DumEDSExcludePattern(p, source)).toList());
        return source;
    }
}
