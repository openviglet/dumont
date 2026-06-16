/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler.fetch;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetcher;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the {@link DumWCFetcher} a source should crawl with (roadmap D07). Every {@code
 * DumWCFetcher} bean on the classpath is registered by its {@link DumWCFetcher#name() name}; a source
 * selects one through its {@code fetcher} config, falling back to the {@code dumont.wc.fetcher}
 * default (itself defaulting to {@link DumWCJsoupFetcher#NAME jsoup}). An unknown name resolves to the
 * default rather than failing the crawl.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumWCFetcherRegistry {

    private final Map<String, DumWCFetcher> byName = new HashMap<>();
    private final DumWCFetcher defaultFetcher;

    public DumWCFetcherRegistry(List<DumWCFetcher> fetchers,
            @Value("${dumont.wc.fetcher:jsoup}") String defaultName) {
        fetchers.forEach(fetcher -> byName.put(key(fetcher.name()), fetcher));
        DumWCFetcher resolved = byName.get(key(defaultName));
        if (resolved == null) {
            resolved = byName.get(DumWCJsoupFetcher.NAME);
            log.warn("Configured default fetcher '{}' not found; using '{}'", defaultName,
                    DumWCJsoupFetcher.NAME);
        }
        this.defaultFetcher = resolved;
        log.info("Web crawler fetchers registered: {} (default: {})", byName.keySet(),
                defaultFetcher.name());
    }

    /** The fetcher for the given config name, or the default when blank/unknown. */
    public DumWCFetcher resolve(String name) {
        if (name == null || name.isBlank()) {
            return defaultFetcher;
        }
        return byName.getOrDefault(key(name), defaultFetcher);
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
