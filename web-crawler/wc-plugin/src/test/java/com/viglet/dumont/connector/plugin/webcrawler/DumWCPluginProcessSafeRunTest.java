/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.plugin.webcrawler.fetch.DumWCFetcherRegistry;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCAllowUrl;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCStartingPoint;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCAttributeMappingRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCFileExtensionRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCNotAllowUrlRepository;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCStartingPointRepository;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchException;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchRequest;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchResponse;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetcher;

/**
 * Coverage for roadmap D09 — the safe-run policy. A crawl that produces no indexable item finishes
 * as <em>standalone</em> so the shared context skips its destructive deindex pass (the previously
 * indexed data survives a total failure), and the run always leaves a per-source failure report. A
 * run that indexes at least one item finishes normally, so stale documents are still deindexed.
 */
@DisplayName("DumWCPluginProcess safe-run policy (D09)")
class DumWCPluginProcessSafeRunTest {

    private static final String WEBSITE = "https://example.com";
    private static final String PAGE = "/page";

    @Test
    @DisplayName("A total-failure run keeps the index (standalone) and writes a failure report")
    void totalFailureKeepsIndex() {
        DumConnectorContext context = mock(DumConnectorContext.class);
        DumWCFetcher fetcher = fetcher(request -> {
            throw new DumWCFetchException(404, "Not Found", null);
        });
        DumWCPluginProcess process = newProcess(context, fetcher, false);

        process.start(source());

        // Standalone finish → the context skips deindex, so previously indexed data survives.
        verify(context).finishIndexing(any(DumConnectorSession.class), eq(true));

        DumWCCrawlReport report = process.lastReport();
        assertTrue(report.isTotalFailure());
        assertEquals(0, report.indexedCount());
        assertEquals(1, report.failureCount());
        assertEquals(404, report.failures().getFirst().statusCode());
    }

    @Test
    @DisplayName("A run that indexes an item finishes normally (deindex stale documents)")
    void successfulRunDeindexes() {
        DumConnectorContext context = mock(DumConnectorContext.class);
        when(context.addJobItem(any())).thenReturn(true);
        byte[] html = ("<html><head><title>t</title></head><body><p>hi</p></body></html>")
                .getBytes(StandardCharsets.UTF_8);
        DumWCFetcher fetcher = fetcher(
                request -> new DumWCFetchResponse(200, "text/html", html, WEBSITE + PAGE));
        DumWCPluginProcess process = newProcess(context, fetcher, true);

        process.start(source());

        verify(context).finishIndexing(any(DumConnectorSession.class), eq(false));

        DumWCCrawlReport report = process.lastReport();
        assertFalse(report.isTotalFailure());
        assertEquals(1, report.indexedCount());
        assertEquals(0, report.failureCount());
    }

    /**
     * A single-worker process wired with the given fetcher and context; every repository is empty
     * except the starting points (one {@code /page}) and, when {@code allowAll}, an allow rule that
     * lets that page be indexed.
     */
    private static DumWCPluginProcess newProcess(DumConnectorContext context, DumWCFetcher fetcher,
            boolean allowAll) {
        DumWCAllowUrlRepository allowRepo = mock(DumWCAllowUrlRepository.class);
        DumWCNotAllowUrlRepository notAllowRepo = mock(DumWCNotAllowUrlRepository.class);
        DumWCFileExtensionRepository extRepo = mock(DumWCFileExtensionRepository.class);
        DumWCAttributeMappingRepository attrRepo = mock(DumWCAttributeMappingRepository.class);
        DumWCStartingPointRepository startRepo = mock(DumWCStartingPointRepository.class);
        DumWCFetcherRegistry registry = mock(DumWCFetcherRegistry.class);

        when(startRepo.findByDumWCSource(any()))
                .thenReturn(Optional.of(List.of(new DumWCStartingPoint(PAGE, null))));
        if (allowAll) {
            when(allowRepo.findByDumWCSource(any()))
                    .thenReturn(Optional.of(List.of(new DumWCAllowUrl("/*", null))));
        }
        when(registry.resolve(any())).thenReturn(fetcher);

        return new DumWCPluginProcess(2000, "https://www.google.com", 30_000L, 3, 0, 0L, 0L, 1,
                allowRepo, notAllowRepo, extRepo, attrRepo, startRepo, registry, context);
    }

    /** A no-op-named {@link DumWCFetcher} delegating its single fetch to {@code behavior}. */
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
