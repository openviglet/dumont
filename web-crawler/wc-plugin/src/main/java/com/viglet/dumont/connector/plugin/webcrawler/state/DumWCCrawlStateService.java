/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler.state;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCCrawlState;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.model.DumWCSource;
import com.viglet.dumont.connector.plugin.webcrawler.persistence.repository.DumWCCrawlStateRepository;

/**
 * Owns the per-(source, url) conditional-fetch state used for incremental re-crawls (roadmap D12).
 * A thin wrapper over the JPA repository so {@code DumWCPluginProcess} keeps its crawl-loop focus —
 * mirroring the EDS connector's crawl-state service.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class DumWCCrawlStateService {

    private final DumWCCrawlStateRepository repository;

    public DumWCCrawlStateService(DumWCCrawlStateRepository repository) {
        this.repository = repository;
    }

    /** Loads every crawl-state row for a source, keyed by URL (preserves insert order). */
    @Transactional(readOnly = true)
    public Map<String, DumWCCrawlState> loadByUrl(DumWCSource source) {
        Map<String, DumWCCrawlState> map = new LinkedHashMap<>();
        for (DumWCCrawlState state : repository.findByDumWCSource(source)) {
            map.put(state.getUrl(), state);
        }
        return map;
    }

    /**
     * True when the prior state's {@code sitemapLastmod} matches the {@code <lastmod>} just observed
     * in the sitemap (both non-blank) — i.e. the URL can be skipped before any fetch. Missing prior
     * state or a blank value on either side means we must fetch.
     */
    public boolean isSitemapUnchanged(DumWCCrawlState prior, String currentLastmod) {
        if (prior == null) {
            return false;
        }
        String stored = prior.getSitemapLastmod();
        return stored != null && !stored.isBlank()
                && currentLastmod != null && !currentLastmod.isBlank()
                && stored.equals(currentLastmod);
    }

    /**
     * Upserts the state row after a successful fetch+index. Touches {@code lastSeenAt}; refreshes
     * {@code lastIndexedAt} only when {@code indexed=true}. Validators ({@code etag},
     * {@code lastModified}, {@code sitemapLastmod}) and {@code checksum} are overwritten only when a
     * non-blank value is supplied, so a fetch that omits a header does not erase a previously stored
     * validator.
     */
    @Transactional
    public DumWCCrawlState upsert(DumWCSource source, String url, String etag, String lastModified,
            String sitemapLastmod, String checksum, boolean indexed) {
        Instant now = Instant.now();
        DumWCCrawlState state = repository.findByDumWCSourceAndUrl(source, url)
                .orElseGet(() -> newState(source, url));
        if (etag != null && !etag.isBlank()) {
            state.setEtag(etag);
        }
        if (lastModified != null && !lastModified.isBlank()) {
            state.setLastModified(lastModified);
        }
        if (sitemapLastmod != null && !sitemapLastmod.isBlank()) {
            state.setSitemapLastmod(sitemapLastmod);
        }
        if (checksum != null && !checksum.isBlank()) {
            state.setChecksum(checksum);
        }
        state.setLastSeenAt(now);
        if (indexed) {
            state.setLastIndexedAt(now);
        }
        return repository.save(state);
    }

    /** Touches {@code lastSeenAt} only — for a page skipped (304 / sitemap-unchanged) this crawl. */
    @Transactional
    public DumWCCrawlState touch(DumWCCrawlState prior) {
        prior.setLastSeenAt(Instant.now());
        return repository.save(prior);
    }

    /**
     * Clears every crawl-state row for a source. With no prior state the next crawl revalidates
     * nothing and re-fetches every URL — the way to force a full re-crawl.
     */
    @Transactional
    public int deleteAll(DumWCSource source) {
        return repository.deleteByDumWCSource(source);
    }

    private static DumWCCrawlState newState(DumWCSource source, String url) {
        DumWCCrawlState state = new DumWCCrawlState();
        state.setDumWCSource(source);
        state.setUrl(url);
        return state;
    }
}
