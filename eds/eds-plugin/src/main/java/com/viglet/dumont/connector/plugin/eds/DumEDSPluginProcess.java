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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.jsoup.nodes.Document;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.eds.commons.DumEDSContext;
import com.viglet.dumont.connector.eds.commons.domain.DumEDSDiscoveredPage;
import com.viglet.dumont.connector.eds.commons.domain.DumEDSFetchedPage;
import com.viglet.dumont.connector.eds.commons.ext.DumEDSExtLocaleInterface;
import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSIndexClient;
import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSPathFilter;
import com.viglet.dumont.connector.plugin.eds.discovery.DumEDSSitemapClient;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSBodyExtractor;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSDocumentMerger;
import com.viglet.dumont.connector.plugin.eds.extract.DumEDSMetadataExtractor;
import com.viglet.dumont.connector.plugin.eds.fetch.DumEDSHttpFetcher;
import com.viglet.dumont.connector.plugin.eds.fetch.DumEDSRateLimiter;
import com.viglet.dumont.connector.plugin.eds.job.DumEDSJobItemBuilder;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSCrawlState;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSExcludePattern;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSIndex;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSRemoveSelector;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSSource;
import com.viglet.dumont.connector.plugin.eds.state.DumEDSCrawlStateService;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the EDS crawl pipeline. Phase 3 adds the fetch + extract + merge
 * phase. Indexing (push to TurSNJobItem) lands in Phase 4.
 */
@Slf4j
@Component
public class DumEDSPluginProcess {

    static final String MDC_SOURCE = "edsSource";
    static final String MDC_PHASE = "phase";
    static final String MDC_URL = "url";
    static final String PHASE_DISCOVERY = "discovery";
    static final String PHASE_SITEMAP = "sitemap";
    static final String PHASE_FILTER = "filter";
    static final String PHASE_FETCH = "fetch";
    static final String PHASE_INDEX = "index";
    static final String PHASE_CLEANUP = "cleanup";

    private final DumEDSIndexClient indexClient;
    private final DumEDSSitemapClient sitemapClient;
    private final DumEDSPathFilter pathFilter;
    private final DumEDSHttpFetcher httpFetcher;
    private final DumEDSMetadataExtractor metadataExtractor;
    private final DumEDSBodyExtractor bodyExtractor;
    private final DumEDSDocumentMerger merger;
    private final DumEDSJobItemBuilder jobItemBuilder;
    private final DumConnectorContext connectorContext;
    private final DumEDSCrawlStateService crawlStateService;

    public DumEDSPluginProcess(DumEDSIndexClient indexClient, DumEDSSitemapClient sitemapClient,
            DumEDSPathFilter pathFilter, DumEDSHttpFetcher httpFetcher,
            DumEDSMetadataExtractor metadataExtractor, DumEDSBodyExtractor bodyExtractor,
            DumEDSDocumentMerger merger, DumEDSJobItemBuilder jobItemBuilder,
            DumConnectorContext connectorContext,
            DumEDSCrawlStateService crawlStateService) {
        this.indexClient = indexClient;
        this.sitemapClient = sitemapClient;
        this.pathFilter = pathFilter;
        this.httpFetcher = httpFetcher;
        this.metadataExtractor = metadataExtractor;
        this.bodyExtractor = bodyExtractor;
        this.merger = merger;
        this.jobItemBuilder = jobItemBuilder;
        this.connectorContext = connectorContext;
        this.crawlStateService = crawlStateService;
    }

    /**
     * Full pipeline: discover → diff against stored state → fetch only changed
     * pages → build job items → enqueue → emit DELETE for paths that have
     * disappeared (or returned 404). Returns the fetched pages mainly so tests
     * can introspect; production code triggers indexing via the side effect on
     * {@link DumConnectorContext}.
     */
    public List<DumEDSFetchedPage> start(DumEDSSource source) {
        MDC.put(MDC_SOURCE, source.getTitle() == null ? source.getId() : source.getTitle());
        DumConnectorSession session = new DumConnectorSession(source.getId(),
                DumEDSJobItemBuilder.PROVIDER_NAME, source.getLocale());
        try {
            List<DumEDSDiscoveredPage> discovered = discoverAndFilter(source);
            Map<String, DumEDSCrawlState> priorStates = crawlStateService.loadByPath(source);

            // Removal detection: any prior path that's no longer in discovery.
            removeMissingPaths(source, session, priorStates, discovered);

            if (discovered.isEmpty()) {
                return List.of();
            }

            // Incremental: skip pages where index lastModified equals stored value.
            List<DumEDSDiscoveredPage> toFetch = filterUnchanged(priorStates, discovered);
            log.info("Incremental: {} to fetch, {} skipped (unchanged)",
                    toFetch.size(), discovered.size() - toFetch.size());

            FetchPhaseResult phase = fetchAll(source, toFetch);
            removeNotFoundPaths(source, session, priorStates, phase.notFoundPaths());
            pushToIndexingPipeline(source, session, phase.fetched());
            return phase.fetched();
        } finally {
            connectorContext.finishIndexing(session, false);
            MDC.remove(MDC_PHASE);
            MDC.remove(MDC_SOURCE);
        }
    }

    /**
     * Full reindex: drops the source's crawl state so the incremental skip and
     * removal-detection passes start from a clean slate, then runs the normal
     * pipeline. Every discovered page is re-fetched and re-enqueued regardless
     * of its {@code lastModified}.
     */
    public List<DumEDSFetchedPage> reindex(DumEDSSource source) {
        int cleared = crawlStateService.deleteAll(source);
        log.info("Reindex: cleared {} crawl-state row(s) for source {}", cleared,
                source.getTitle());
        return start(source);
    }

    /**
     * Dry scan: runs discovery + sitemap merge + exclude filtering only — no
     * fetching, no indexing, no state mutation. Returns what <em>would</em> be
     * crawled so an operator can validate indexes/exclude patterns before
     * committing to a full run.
     */
    public DryScanResult dryScan(DumEDSSource source) {
        List<DumEDSDiscoveredPage> discovered = discoverAndFilter(source);
        List<String> paths = discovered.stream().map(DumEDSDiscoveredPage::path).toList();
        log.info("Dry scan for source {}: {} page(s) would be crawled", source.getTitle(),
                paths.size());
        return new DryScanResult(paths.size(), paths);
    }

    /** Outcome of a {@link #dryScan(DumEDSSource)} — the count and paths that would be crawled. */
    public record DryScanResult(int discovered, List<String> paths) {
    }

    private void removeNotFoundPaths(DumEDSSource source, DumConnectorSession session,
            Map<String, DumEDSCrawlState> priorStates, Set<String> notFoundPaths) {
        if (notFoundPaths.isEmpty()) {
            return;
        }
        MDC.put(MDC_PHASE, PHASE_CLEANUP);
        int gone = 0;
        for (String path : notFoundPaths) {
            // Only emit DELETE for paths that were previously indexed. A 404 on a
            // path we never indexed is just a transient discovery/source mismatch
            // and shouldn't pollute the queue.
            if (priorStates.containsKey(path) && enqueueDelete(source, session, path)) {
                crawlStateService.delete(source, path);
                gone++;
            }
        }
        if (gone > 0) {
            log.info("Cleanup: emitted DELETE for {} 404-gone path(s)", gone);
        }
    }

    private List<DumEDSDiscoveredPage> filterUnchanged(Map<String, DumEDSCrawlState> priorStates,
            List<DumEDSDiscoveredPage> discovered) {
        List<DumEDSDiscoveredPage> toFetch = new ArrayList<>();
        for (DumEDSDiscoveredPage page : discovered) {
            DumEDSCrawlState prior = priorStates.get(page.path());
            String currentLastMod = page.metadata().get("lastModified");
            if (crawlStateService.isUnchanged(prior, currentLastMod)) {
                crawlStateService.touch(prior);
            } else {
                toFetch.add(page);
            }
        }
        return toFetch;
    }

    private void removeMissingPaths(DumEDSSource source, DumConnectorSession session,
            Map<String, DumEDSCrawlState> priorStates, List<DumEDSDiscoveredPage> discovered) {
        MDC.put(MDC_PHASE, PHASE_CLEANUP);
        Set<String> currentPaths = new LinkedHashSet<>();
        for (DumEDSDiscoveredPage page : discovered) {
            currentPaths.add(page.path());
        }
        int deleted = 0;
        for (Map.Entry<String, DumEDSCrawlState> entry : priorStates.entrySet()) {
            if (!currentPaths.contains(entry.getKey())
                    && enqueueDelete(source, session, entry.getKey())) {
                crawlStateService.delete(source, entry.getKey());
                deleted++;
            }
        }
        if (deleted > 0) {
            log.info("Cleanup: emitted DELETE for {} removed path(s)", deleted);
        }
    }

    private boolean enqueueDelete(DumEDSSource source, DumConnectorSession session, String path) {
        try {
            TurSNJobItem deleteItem = jobItemBuilder.buildDelete(source, path);
            DumJobItemWithSession item = new DumJobItemWithSession(deleteItem, session,
                    java.util.Collections.emptySet(), false);
            return connectorContext.addJobItem(item);
        } catch (RuntimeException e) {
            log.warn("Failed to enqueue DELETE for {}: {}", path, e.getMessage());
            return false;
        }
    }

    private void pushToIndexingPipeline(DumEDSSource source, DumConnectorSession session,
            List<DumEDSFetchedPage> fetched) {
        MDC.put(MDC_PHASE, PHASE_INDEX);
        int accepted = 0;
        for (DumEDSFetchedPage page : fetched) {
            try {
                TurSNJobItem turItem = jobItemBuilder.build(source, page);
                DumJobItemWithSession item = new DumJobItemWithSession(turItem, session,
                        java.util.Collections.emptySet(), false);
                if (connectorContext.addJobItem(item)) {
                    accepted++;
                    crawlStateService.upsert(source, page.path(), page.lastModified(),
                            turItem.getChecksum(), true);
                }
            } catch (RuntimeException e) {
                log.warn("Failed to enqueue job item for {}: {}", page.path(), e.getMessage());
            }
        }
        log.info("Index phase complete: {}/{} job items enqueued", accepted, fetched.size());
    }

    /**
     * Discovery + sitemap + filter — equivalent to the Phase 2 public entry. Kept
     * separate so tests targeting the pre-fetch pipeline don't need to wire HTTP
     * clients.
     */
    public List<DumEDSDiscoveredPage> discoverAndFilter(DumEDSSource source) {
        MDC.put(MDC_PHASE, PHASE_DISCOVERY);
        log.info("Starting EDS discovery for source {} (baseUrl={})", source.getTitle(),
                source.getBaseUrl());
        Map<String, DumEDSDiscoveredPage> byPath = collectFromIndexes(source);
        MDC.put(MDC_PHASE, PHASE_SITEMAP);
        mergeSitemap(source, byPath);
        MDC.put(MDC_PHASE, PHASE_FILTER);
        return applyExcludePatterns(source, byPath);
    }

    /**
     * Outcome of the per-page fetch task: at most one of {@code page} (success)
     * or {@code notFound} (HTTP 404/410) is non-null; both null means a
     * transient failure that the caller treats as "skip and move on".
     */
    private record FetchOneOutcome(String path, DumEDSFetchedPage page, boolean notFound) {
        static FetchOneOutcome ok(DumEDSFetchedPage page) {
            return new FetchOneOutcome(page.path(), page, false);
        }

        static FetchOneOutcome gone(String path) {
            return new FetchOneOutcome(path, null, true);
        }

        static FetchOneOutcome failed(String path) {
            return new FetchOneOutcome(path, null, false);
        }
    }

    /** Aggregate of a fetch phase. {@code notFoundPaths} feed cleanup; {@code fetched} feed indexing. */
    record FetchPhaseResult(List<DumEDSFetchedPage> fetched, Set<String> notFoundPaths,
            int failureCount) {
    }

    private FetchPhaseResult fetchAll(DumEDSSource source,
            List<DumEDSDiscoveredPage> discovered) {
        MDC.put(MDC_PHASE, PHASE_FETCH);
        DumEDSHttpFetcher.Settings fetchSettings = settingsFor(source);
        DumEDSRateLimiter rateLimiter = new DumEDSRateLimiter(fetchSettings.permitsPerSecond());
        Collection<String> removeSelectors = removeSelectorsOf(source);
        int concurrency = source.getMaxConcurrent() == null || source.getMaxConcurrent() <= 0
                ? 8
                : source.getMaxConcurrent();
        Semaphore inflight = new Semaphore(concurrency);
        log.info("Fetching {} page(s) with concurrency={} permitsPerSecond={}", discovered.size(),
                concurrency, fetchSettings.permitsPerSecond());

        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<FetchOneOutcome>> futures = new ArrayList<>();
            for (DumEDSDiscoveredPage page : discovered) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> fetchOne(source, page, fetchSettings, rateLimiter, inflight,
                                removeSelectors),
                        exec));
            }
            List<DumEDSFetchedPage> succeeded = new ArrayList<>();
            Set<String> notFound = new LinkedHashSet<>();
            int failures = 0;
            for (CompletableFuture<FetchOneOutcome> f : futures) {
                FetchOneOutcome outcome = f.join();
                if (outcome.page() != null) {
                    succeeded.add(outcome.page());
                } else if (outcome.notFound()) {
                    notFound.add(outcome.path());
                } else {
                    failures++;
                }
            }
            log.info("Fetch complete: {} succeeded, {} not-found, {} failed (of {})",
                    succeeded.size(), notFound.size(), failures, discovered.size());
            return new FetchPhaseResult(succeeded, notFound, failures);
        }
    }

    private FetchOneOutcome fetchOne(DumEDSSource source, DumEDSDiscoveredPage page,
            DumEDSHttpFetcher.Settings fetchSettings, DumEDSRateLimiter rateLimiter,
            Semaphore inflight, Collection<String> removeSelectors) {
        try {
            inflight.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FetchOneOutcome.failed(page.path());
        }
        URI url = URI.create(source.getBaseUrl()
                + (page.path().startsWith("/") ? page.path() : "/" + page.path()));
        MDC.put(MDC_URL, url.toString());
        try {
            DumEDSHttpFetcher.FetchResult result = httpFetcher.fetch(url, fetchSettings,
                    rateLimiter);
            Document doc = result.document();
            Map<String, String> htmlMetadata = metadataExtractor.extract(doc);
            String body = bodyExtractor.extract(doc, source.getBodySelector(), removeSelectors);
            Map<String, String> merged = merger.merge(page.metadata(), htmlMetadata);
            String lastModified = result.lastModified()
                    .orElseGet(() -> page.metadata().getOrDefault("lastModified", null));
            Locale resolvedLocale = resolveLocaleFromExtension(source, page, url, doc);
            return FetchOneOutcome.ok(
                    new DumEDSFetchedPage(page.path(), merged, body, lastModified, null,
                            resolvedLocale));
        } catch (DumEDSHttpFetcher.NotFoundException e) {
            log.info("404/410 for {} — will purge from index if previously stored", url);
            return FetchOneOutcome.gone(page.path());
        } catch (RuntimeException e) {
            log.warn("Failed to fetch {}: {}", page.path(), e.getMessage());
            return FetchOneOutcome.failed(page.path());
        } finally {
            MDC.remove(MDC_URL);
            inflight.release();
        }
    }

    /**
     * Locale resolution happens here (per-page) so a single source can index
     * documents with different locales — e.g. a site serving {@code /pt-br/...}
     * and {@code /en-us/...} pages under one configuration. When the source has
     * an explicit locale we short-circuit; otherwise we invoke the configured
     * {@code DumEDSExtLocaleInterface} via {@link DumCustomClassCache}. Any
     * error in the extension is logged and downgraded to "no per-page locale"
     * — the builder later falls back to source/{@code Locale.US}.
     */
    static Locale resolveLocaleFromExtension(DumEDSSource source, DumEDSDiscoveredPage page,
            URI url, Document doc) {
        if (source.getLocale() != null) {
            return null;  // source's explicit locale wins downstream; no per-page override.
        }
        String localeClass = source.getLocaleClass();
        if (localeClass == null || localeClass.isBlank()) {
            return null;
        }
        DumEDSContext context = DumEDSContext.builder()
                .path(page.path())
                .url(url.toString())
                .baseUrl(source.getBaseUrl())
                .document(doc)
                .jsonMetadata(page.metadata())
                .userAgent(source.getUserAgent())
                .build();
        try {
            return DumCustomClassCache.getCustomClassMap(localeClass)
                    .map(instance -> ((DumEDSExtLocaleInterface) instance).consume(context))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("Locale extension {} failed for {}: {}", localeClass, page.path(),
                    e.getMessage());
            return null;
        }
    }

    static DumEDSHttpFetcher.Settings settingsFor(DumEDSSource source) {
        return new DumEDSHttpFetcher.Settings(
                source.getUserAgent(),
                source.getFetchTimeoutMs() == null ? 0 : source.getFetchTimeoutMs(),
                source.getRequestsPerSecond() == null ? 0d : source.getRequestsPerSecond(),
                DumEDSHttpFetcher.DEFAULT_MAX_RETRIES);
    }

    private Map<String, DumEDSDiscoveredPage> collectFromIndexes(DumEDSSource source) {
        Map<String, DumEDSDiscoveredPage> byPath = new LinkedHashMap<>();
        Collection<DumEDSIndex> indexes = source.getIndexes();
        if (indexes == null || indexes.isEmpty()) {
            log.warn("No indexes configured for source {}", source.getTitle());
            return byPath;
        }
        for (DumEDSIndex index : indexes) {
            List<Map<String, String>> rows = indexClient.fetchAllRows(source.getBaseUrl(),
                    index.getPath(), source.getUserAgent());
            log.info("Index {}{} yielded {} rows", source.getBaseUrl(), index.getPath(),
                    rows.size());
            for (Map<String, String> row : rows) {
                String path = row.get("path");
                if (path == null || path.isBlank()) {
                    continue;
                }
                byPath.putIfAbsent(path, DumEDSDiscoveredPage.fromIndex(path, row));
            }
        }
        log.info("Aggregated {} unique paths from {} index(es)", byPath.size(), indexes.size());
        return byPath;
    }

    private void mergeSitemap(DumEDSSource source, Map<String, DumEDSDiscoveredPage> byPath) {
        String sitemapPath = source.getSitemapFallback();
        if (sitemapPath == null || sitemapPath.isBlank()) {
            return;
        }
        Set<String> sitemapPaths = sitemapClient.fetchPaths(source.getBaseUrl(), sitemapPath,
                source.getUserAgent());
        if (sitemapPaths.isEmpty()) {
            return;
        }
        Set<String> indexPaths = new LinkedHashSet<>(byPath.keySet());
        Set<String> extras = new LinkedHashSet<>(sitemapPaths);
        extras.removeAll(indexPaths);
        Set<String> missingFromSitemap = new LinkedHashSet<>(indexPaths);
        missingFromSitemap.removeAll(sitemapPaths);
        if (!extras.isEmpty()) {
            log.warn("Sitemap has {} path(s) absent from indexes — adding to discovery: {}",
                    extras.size(), summarise(extras));
            for (String extra : extras) {
                byPath.putIfAbsent(extra, DumEDSDiscoveredPage.fromSitemap(extra));
            }
        }
        if (!missingFromSitemap.isEmpty()) {
            log.warn("{} path(s) present in indexes but absent from sitemap: {}",
                    missingFromSitemap.size(), summarise(missingFromSitemap));
        }
    }

    private List<DumEDSDiscoveredPage> applyExcludePatterns(DumEDSSource source,
            Map<String, DumEDSDiscoveredPage> byPath) {
        List<String> patterns = patternsOf(source);
        if (patterns.isEmpty()) {
            return new ArrayList<>(byPath.values());
        }
        List<DumEDSDiscoveredPage> kept = new ArrayList<>();
        int filtered = 0;
        for (DumEDSDiscoveredPage page : byPath.values()) {
            if (pathFilter.isExcluded(page.path(), patterns)) {
                filtered++;
            } else {
                kept.add(page);
            }
        }
        log.info("Filter: {} kept, {} dropped against {} pattern(s)", kept.size(), filtered,
                patterns.size());
        return kept;
    }

    private static List<String> patternsOf(DumEDSSource source) {
        Collection<DumEDSExcludePattern> patterns = source.getExcludePatterns();
        if (patterns == null) {
            return List.of();
        }
        return patterns.stream().map(DumEDSExcludePattern::getPattern)
                .filter(p -> p != null && !p.isBlank()).toList();
    }

    private static Collection<String> removeSelectorsOf(DumEDSSource source) {
        Collection<DumEDSRemoveSelector> selectors = source.getRemoveSelectors();
        if (selectors == null) {
            return Collections.emptyList();
        }
        return selectors.stream().map(DumEDSRemoveSelector::getSelector)
                .filter(s -> s != null && !s.isBlank()).toList();
    }

    private static String summarise(Set<String> paths) {
        int max = 10;
        if (paths.size() <= max) {
            return paths.toString();
        }
        List<String> sample = new ArrayList<>();
        int i = 0;
        for (String path : paths) {
            if (i++ >= max) {
                break;
            }
            sample.add(path);
        }
        return sample + " (+" + (paths.size() - max) + " more)";
    }
}
