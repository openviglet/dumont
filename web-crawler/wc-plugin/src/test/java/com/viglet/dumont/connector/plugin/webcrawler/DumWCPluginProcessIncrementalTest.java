/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumInMemoryCoverageHistoryStore;
import com.viglet.dumont.connector.plugin.webcrawler.fetch.DumWCFetcherRegistry;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCCrawlState;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCStartingPoint;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAttributeMappingRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCFileExtensionRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCNotAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCStartingPointRepository;
import com.viglet.dumont.connector.plugin.webcrawler.state.DumWCCrawlStateService;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchRequest;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchResponse;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetcher;

/**
 * Coverage for roadmap D12 — incremental fetch (HTTP-conditional re-crawl). A re-run of an unchanged
 * source issues conditional requests and re-indexes ~0 documents (the {@code 304} path), and a source
 * advertising a {@code <lastmod>} skips unchanged URLs before any fetch. Skipped pages are re-emitted
 * as unchanged so they survive the safe-run deindex pass.
 */
@DisplayName("DumWCPluginProcess incremental fetch (D12)")
class DumWCPluginProcessIncrementalTest {

    private static final String WEBSITE = "https://example.com";
    private static final String PAGE = "/page";
    private static final String PAGE_URL = WEBSITE + PAGE;
    private static final String HTML = "<html><head><title>t</title></head><body><p>hi</p></body></html>";

    @Test
    @DisplayName("A 304 on a known page issues a conditional request, re-indexes nothing, and keeps it")
    void notModifiedKeepsIndexWithoutReindex() {
        DumConnectorContext context = mock(DumConnectorContext.class);
        when(context.addJobItem(any())).thenReturn(true);
        DumWCCrawlStateService stateService = mock(DumWCCrawlStateService.class);
        DumWCCrawlState prior = priorState("etag-1", "Mon, 01 Jan 2026 00:00:00 GMT", null, "999");
        when(stateService.loadByUrl(any())).thenReturn(new HashMap<>(Map.of(PAGE_URL, prior)));

        AtomicInteger conditionalRequests = new AtomicInteger();
        DumWCFetcher fetcher = fetcher(request -> {
            if (request.conditional()) {
                conditionalRequests.incrementAndGet();
                assertEquals("etag-1", request.ifNoneMatch());
                return new DumWCFetchResponse(304, null, new byte[0], request.url());
            }
            return html200(request.url());
        });

        DumWCPluginProcess process = newProcess(context, fetcher, stateService, true);
        process.start(source());

        // The conditional GET fired and the server answered 304 — no re-index, no body parsed.
        assertEquals(1, conditionalRequests.get());
        DumWCCrawlReport report = process.lastReport();
        assertEquals(0, report.indexedCount());
        assertEquals(1, report.notModifiedCount());
        assertFalse(report.isTotalFailure());

        // The page is re-emitted as unchanged (stored checksum) so the deindex pass spares it.
        verify(stateService).touch(prior);
        ArgumentCaptor<DumJobItemWithSession> captor = ArgumentCaptor.forClass(DumJobItemWithSession.class);
        verify(context).addJobItem(captor.capture());
        assertEquals(PAGE_URL, captor.getValue().turSNJobItem().getId());
        assertEquals("999", captor.getValue().turSNJobItem().getChecksum());
        // A 304 re-run is not a total failure → normal finish (stale documents still deindexed).
        verify(context).finishIndexing(any(DumConnectorSession.class), eq(false));
    }

    @Test
    @DisplayName("A first crawl fetches unconditionally and persists the response validators")
    void firstCrawlPersistsValidators() {
        DumConnectorContext context = mock(DumConnectorContext.class);
        when(context.addJobItem(any())).thenReturn(true);
        DumWCCrawlStateService stateService = mock(DumWCCrawlStateService.class);
        when(stateService.loadByUrl(any())).thenReturn(new HashMap<>());

        DumWCFetcher fetcher = fetcher(request -> {
            // No prior state → no conditional validators on the first crawl.
            assertFalse(request.conditional());
            return new DumWCFetchResponse(200, "text/html", HTML.getBytes(StandardCharsets.UTF_8),
                    request.url(), "etag-1", "Mon, 01 Jan 2026 00:00:00 GMT");
        });

        DumWCPluginProcess process = newProcess(context, fetcher, stateService, true);
        process.start(source());

        assertEquals(1, process.lastReport().indexedCount());
        verify(stateService).upsert(any(DumWCSource.class), eq(PAGE_URL), eq("etag-1"),
                eq("Mon, 01 Jan 2026 00:00:00 GMT"), isNull(), any(), eq(true));
    }

    @Test
    @DisplayName("A sitemap lastmod unchanged since last crawl skips the URL before any fetch")
    void sitemapLastmodSkipsBeforeFetch() {
        DumConnectorContext context = mock(DumConnectorContext.class);
        when(context.addJobItem(any())).thenReturn(true);
        DumWCCrawlStateService stateService = mock(DumWCCrawlStateService.class);
        DumWCCrawlState prior = priorState(null, null, "2026-01-01", "777");
        when(stateService.loadByUrl(any())).thenReturn(new HashMap<>(Map.of(PAGE_URL, prior)));
        when(stateService.isSitemapUnchanged(prior, "2026-01-01")).thenReturn(true);

        AtomicInteger pageFetches = new AtomicInteger();
        DumWCFetcher fetcher = fetcher(request -> {
            String url = request.url();
            if (url.endsWith("/robots.txt")) {
                return text200("", url);
            }
            if (url.endsWith("/sitemap.xml")) {
                return text200("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                          <url><loc>%s</loc><lastmod>2026-01-01</lastmod></url>
                        </urlset>
                        """.formatted(PAGE_URL), url);
            }
            pageFetches.incrementAndGet();
            return html200(url);
        });

        // No starting points: the sitemap is the only discovery source for this run.
        DumWCSource source = source();
        source.setEnableSitemap(true);
        source.setStartingPoints(Set.of());
        DumWCPluginProcess process = newProcess(context, fetcher, stateService, true, source);

        process.start(source);

        // The page was never fetched — the lastmod match short-circuited it.
        assertEquals(0, pageFetches.get());
        assertEquals(1, process.lastReport().notModifiedCount());
        assertEquals(0, process.lastReport().indexedCount());
        verify(stateService).touch(prior);
        ArgumentCaptor<DumJobItemWithSession> captor = ArgumentCaptor.forClass(DumJobItemWithSession.class);
        verify(context).addJobItem(captor.capture());
        assertEquals("777", captor.getValue().turSNJobItem().getChecksum());
    }

    @Test
    @DisplayName("A 304 with no prior checksum keeps no job item (page was never indexed)")
    void notModifiedWithoutPriorChecksumEmitsNothing() {
        DumConnectorContext context = mock(DumConnectorContext.class);
        DumWCCrawlStateService stateService = mock(DumWCCrawlStateService.class);
        DumWCCrawlState prior = priorState("etag-1", null, null, null);
        when(stateService.loadByUrl(any())).thenReturn(new HashMap<>(Map.of(PAGE_URL, prior)));

        DumWCFetcher fetcher = fetcher(request -> new DumWCFetchResponse(304, null, new byte[0],
                request.url()));

        DumWCPluginProcess process = newProcess(context, fetcher, stateService, true);
        process.start(source());

        assertEquals(1, process.lastReport().notModifiedCount());
        verify(context, never()).addJobItem(any());
        verify(stateService, never()).touch(any());
        assertNull(process.lastReport().failures().stream().findFirst().orElse(null));
        assertFalse(process.lastReport().isTotalFailure());
    }

    private static DumWCCrawlState priorState(String etag, String lastModified, String sitemapLastmod,
            String checksum) {
        DumWCCrawlState state = new DumWCCrawlState();
        state.setUrl(PAGE_URL);
        state.setEtag(etag);
        state.setLastModified(lastModified);
        state.setSitemapLastmod(sitemapLastmod);
        state.setChecksum(checksum);
        return state;
    }

    private static DumWCFetchResponse html200(String url) {
        return new DumWCFetchResponse(200, "text/html", HTML.getBytes(StandardCharsets.UTF_8), url);
    }

    private static DumWCFetchResponse text200(String body, String url) {
        return new DumWCFetchResponse(200, "text/xml", body.getBytes(StandardCharsets.UTF_8), url);
    }

    private static DumWCPluginProcess newProcess(DumConnectorContext context, DumWCFetcher fetcher,
            DumWCCrawlStateService stateService, boolean allowAll) {
        return newProcess(context, fetcher, stateService, allowAll, null);
    }

    private static DumWCPluginProcess newProcess(DumConnectorContext context, DumWCFetcher fetcher,
            DumWCCrawlStateService stateService, boolean allowAll, DumWCSource preconfiguredSource) {
        DumWCAllowUrlRepository allowRepo = mock(DumWCAllowUrlRepository.class);
        DumWCNotAllowUrlRepository notAllowRepo = mock(DumWCNotAllowUrlRepository.class);
        DumWCFileExtensionRepository extRepo = mock(DumWCFileExtensionRepository.class);
        DumWCAttributeMappingRepository attrRepo = mock(DumWCAttributeMappingRepository.class);
        DumWCStartingPointRepository startRepo = mock(DumWCStartingPointRepository.class);
        DumWCFetcherRegistry registry = mock(DumWCFetcherRegistry.class);

        boolean withStartingPoint = preconfiguredSource == null
                || (preconfiguredSource.getStartingPoints() != null
                        && !preconfiguredSource.getStartingPoints().isEmpty());
        when(startRepo.findByDumWCSource(any())).thenReturn(withStartingPoint
                ? Optional.of(List.of(new DumWCStartingPoint(PAGE, null)))
                : Optional.of(List.of()));
        if (allowAll) {
            when(allowRepo.findByDumWCSource(any()))
                    .thenReturn(Optional.of(List.of(new DumWCAllowUrl("/*", null))));
        }
        when(registry.resolve(any())).thenReturn(fetcher);

        return new DumWCPluginProcess(2000, "https://www.google.com", 30_000L, 3, 0, 0L, 0L, 1,
                allowRepo, notAllowRepo, extRepo, attrRepo, startRepo, registry, stateService,
                context, new DumInMemoryCoverageHistoryStore());
    }

    /** A no-op-named {@link DumWCFetcher} delegating each fetch to {@code behavior}. */
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
