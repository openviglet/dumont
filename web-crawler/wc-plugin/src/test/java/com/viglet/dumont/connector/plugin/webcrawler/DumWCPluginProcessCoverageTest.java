/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageHistoryStore;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumFieldCoverage;
import com.viglet.dumont.connector.plugin.webcrawler.fetch.DumWCFetcherRegistry;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAttributeMapping;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCStartingPoint;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAttributeMappingRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCFileExtensionRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCNotAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCStartingPointRepository;
import com.viglet.dumont.connector.plugin.webcrawler.state.DumWCCrawlStateService;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchException;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchRequest;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchResponse;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetcher;

/**
 * Coverage for roadmap D23 — a web-crawler source reports per-field coverage to the shared D15
 * history without becoming a structured source. A run that indexes pages records one coverage
 * snapshot keyed by the source id over the declared mapping names; a run that declares no mappings,
 * or indexes nothing, records none (so an all-failed run does not raise a spurious 0%-coverage drop).
 */
@DisplayName("DumWCPluginProcess coverage telemetry (D23)")
class DumWCPluginProcessCoverageTest {

    private static final String WEBSITE = "https://example.com";
    private static final String PAGE = "/page";

    private static Map<String, DumFieldCoverage> byField(List<DumFieldCoverage> coverage) {
        return coverage.stream().collect(Collectors.toMap(DumFieldCoverage::field, c -> c));
    }

    @Test
    @DisplayName("An indexed run records a coverage snapshot keyed by source id")
    void indexedRunRecordsCoverage() {
        DumConnectorContext context = mock(DumConnectorContext.class);
        when(context.addJobItem(any())).thenReturn(true);
        DumCoverageHistoryStore store = mock(DumCoverageHistoryStore.class);
        byte[] html = "<html><head><title>t</title></head><body><p>hi</p></body></html>"
                .getBytes(StandardCharsets.UTF_8);
        DumWCFetcher fetcher = fetcher(
                request -> new DumWCFetchResponse(200, "text/html", html, WEBSITE + PAGE));

        // A static-text mapping is present on every indexed page.
        DumWCPluginProcess process = newProcess(context, fetcher, store, true,
                new DumWCAttributeMapping("category", "news", null));

        process.start(source());

        ArgumentCaptor<List<DumFieldCoverage>> fields = ArgumentCaptor.captor();
        verify(store).record(eq("s1"), eq(1), fields.capture());
        Map<String, DumFieldCoverage> coverage = byField(fields.getValue());
        assertEquals(1, coverage.get("category").present());
        assertEquals(1, coverage.get("category").total());
        assertEquals(100, coverage.get("category").percent());
    }

    @Test
    @DisplayName("A source with no declared mappings records no coverage")
    void noMappingsRecordsNothing() {
        DumConnectorContext context = mock(DumConnectorContext.class);
        when(context.addJobItem(any())).thenReturn(true);
        DumCoverageHistoryStore store = mock(DumCoverageHistoryStore.class);
        byte[] html = "<html><head><title>t</title></head><body><p>hi</p></body></html>"
                .getBytes(StandardCharsets.UTF_8);
        DumWCFetcher fetcher = fetcher(
                request -> new DumWCFetchResponse(200, "text/html", html, WEBSITE + PAGE));
        DumWCPluginProcess process = newProcess(context, fetcher, store, true);

        process.start(source());

        verify(store, never()).record(anyString(), anyInt(), anyList());
    }

    @Test
    @DisplayName("A total-failure run records no coverage (no spurious 0% drop)")
    void totalFailureRecordsNothing() {
        DumConnectorContext context = mock(DumConnectorContext.class);
        DumCoverageHistoryStore store = mock(DumCoverageHistoryStore.class);
        DumWCFetcher fetcher = fetcher(request -> {
            throw new DumWCFetchException(404, "Not Found", null);
        });
        DumWCPluginProcess process = newProcess(context, fetcher, store, true,
                new DumWCAttributeMapping("category", "news", null));

        process.start(source());

        verify(store, never()).record(anyString(), anyInt(), anyList());
    }

    private static DumWCPluginProcess newProcess(DumConnectorContext context, DumWCFetcher fetcher,
            DumCoverageHistoryStore store, boolean allowAll, DumWCAttributeMapping... mappings) {
        DumWCAllowUrlRepository allowRepo = mock(DumWCAllowUrlRepository.class);
        DumWCNotAllowUrlRepository notAllowRepo = mock(DumWCNotAllowUrlRepository.class);
        DumWCFileExtensionRepository extRepo = mock(DumWCFileExtensionRepository.class);
        DumWCAttributeMappingRepository attrRepo = mock(DumWCAttributeMappingRepository.class);
        DumWCStartingPointRepository startRepo = mock(DumWCStartingPointRepository.class);
        DumWCFetcherRegistry registry = mock(DumWCFetcherRegistry.class);
        DumWCCrawlStateService crawlStateService = mock(DumWCCrawlStateService.class);

        when(startRepo.findByDumWCSource(any()))
                .thenReturn(Optional.of(List.of(new DumWCStartingPoint(PAGE, null))));
        if (allowAll) {
            when(allowRepo.findByDumWCSource(any()))
                    .thenReturn(Optional.of(List.of(new DumWCAllowUrl("/*", null))));
        }
        if (mappings.length > 0) {
            when(attrRepo.findByDumWCSource(any())).thenReturn(Optional.of(List.of(mappings)));
        }
        when(registry.resolve(any())).thenReturn(fetcher);
        when(crawlStateService.loadByUrl(any())).thenReturn(new java.util.HashMap<>());

        return new DumWCPluginProcess(2000, "https://www.google.com", 30_000L, 3, 0, 0L, 0L, 1,
                allowRepo, notAllowRepo, extRepo, attrRepo, startRepo, registry, crawlStateService,
                context, store);
    }

    private static DumWCFetcher fetcher(Fetch behavior) {
        return new DumWCFetcher() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public DumWCFetchResponse fetch(DumWCFetchRequest request) throws IOException {
                return behavior.fetch(request);
            }
        };
    }

    @FunctionalInterface
    private interface Fetch {
        DumWCFetchResponse fetch(DumWCFetchRequest request) throws IOException;
    }

    private static DumWCSource source() {
        DumWCSource source = new DumWCSource();
        source.setId("s1");
        source.setUrl(WEBSITE);
        source.setLocale(Locale.US);
        source.setTurSNSites(Set.of("Sample"));
        return source;
    }
}
