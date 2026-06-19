/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler.fetch;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchRequest;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetchResponse;
import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCFetcher;

/**
 * Coverage for roadmap D07 — fetcher selection: a source resolves to its named fetcher, and a blank
 * or unknown name falls back to the default rather than failing the crawl.
 */
@DisplayName("DumWCFetcherRegistry (D07)")
class DumWCFetcherRegistryTest {

    private final DumWCFetcher jsoup = named(DumWCJsoupFetcher.FETCHER_NAME);
    private final DumWCFetcher headless = named("headless");

    @Test
    @DisplayName("A configured name resolves to that fetcher (case-insensitive)")
    void resolvesByName() {
        DumWCFetcherRegistry registry = new DumWCFetcherRegistry(List.of(jsoup, headless), "jsoup");
        assertSame(headless, registry.resolve("headless"));
        assertSame(headless, registry.resolve("  HEADLESS  "));
        assertSame(jsoup, registry.resolve("jsoup"));
    }

    @Test
    @DisplayName("Blank config resolves to the configured default")
    void blankResolvesToDefault() {
        DumWCFetcherRegistry registry = new DumWCFetcherRegistry(List.of(jsoup, headless), "headless");
        assertSame(headless, registry.resolve(null));
        assertSame(headless, registry.resolve("   "));
    }

    @Test
    @DisplayName("Unknown name and unknown default both fall back to jsoup")
    void unknownFallsBack() {
        DumWCFetcherRegistry registry = new DumWCFetcherRegistry(List.of(jsoup, headless), "does-not-exist");
        assertSame(jsoup, registry.resolve("also-missing"));
        assertSame(jsoup, registry.resolve(null));
    }

    private static DumWCFetcher named(String name) {
        return new DumWCFetcher() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public DumWCFetchResponse fetch(DumWCFetchRequest request) {
                throw new UnsupportedOperationException("not exercised in selection tests");
            }
        };
    }
}
