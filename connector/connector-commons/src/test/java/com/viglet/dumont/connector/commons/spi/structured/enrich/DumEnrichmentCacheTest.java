/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.dumont.connector.commons.spi.structured.enrich;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class DumEnrichmentCacheTest {

    @Test
    void resolvesOncePerKeyAndReusesTheResult() {
        DumEnrichmentCache cache = new DumEnrichmentCache(0);
        AtomicInteger calls = new AtomicInteger();

        Optional<String> first = cache.resolve("k", () -> {
            calls.incrementAndGet();
            return Optional.of("label");
        });
        Optional<String> second = cache.resolve("k", () -> {
            calls.incrementAndGet();
            return Optional.of("label");
        });

        assertEquals(Optional.of("label"), first);
        assertEquals(Optional.of("label"), second);
        assertEquals(1, calls.get(), "the loader runs once; the second call is a cache hit");
    }

    @Test
    void cachesNegativeResultsToo() {
        DumEnrichmentCache cache = new DumEnrichmentCache(0);
        AtomicInteger calls = new AtomicInteger();

        cache.resolve("k", () -> {
            calls.incrementAndGet();
            return Optional.empty();
        });
        Optional<String> second = cache.resolve("k", () -> {
            calls.incrementAndGet();
            return Optional.of("should-not-run");
        });

        assertTrue(second.isEmpty(), "a cached 'no label' result is returned without re-running");
        assertEquals(1, calls.get());
    }

    @Test
    void expiresAfterTtl() {
        AtomicLong now = new AtomicLong(1_000);
        DumEnrichmentCache cache = new DumEnrichmentCache(100, now::get);
        AtomicInteger calls = new AtomicInteger();

        cache.resolve("k", () -> {
            calls.incrementAndGet();
            return Optional.of("v1");
        });
        now.set(1_050); // within TTL -> still cached
        cache.resolve("k", () -> {
            calls.incrementAndGet();
            return Optional.of("v2");
        });
        assertEquals(1, calls.get());

        now.set(1_200); // past TTL -> reloaded
        Optional<String> reloaded = cache.resolve("k", () -> {
            calls.incrementAndGet();
            return Optional.of("v3");
        });
        assertEquals(Optional.of("v3"), reloaded);
        assertEquals(2, calls.get());
    }

    @Test
    void clearForcesReload() {
        DumEnrichmentCache cache = new DumEnrichmentCache(0);
        AtomicInteger calls = new AtomicInteger();

        cache.resolve("k", () -> {
            calls.incrementAndGet();
            return Optional.of("v");
        });
        cache.clear();
        cache.resolve("k", () -> {
            calls.incrementAndGet();
            return Optional.of("v");
        });
        assertEquals(2, calls.get());
    }
}
