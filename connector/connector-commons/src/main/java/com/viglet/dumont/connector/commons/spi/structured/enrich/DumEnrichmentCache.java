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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A small, thread-safe TTL cache for classifier results, so the LLM enrichment path is "behind
 * config + cache" (roadmap <strong>D04</strong>): an identical input is classified once and reused,
 * and a negative result ("no label") is cached too, so a thin record is not re-sent on every run.
 *
 * <p>
 * This is a local optimization that complements — does not replace — Turing's server-side
 * {@code TurLlmCacheService}; it spares the network round-trip entirely for repeats within the TTL.
 * A {@code ttlMillis <= 0} means entries never expire (cache for the process lifetime). The clock is
 * injectable for deterministic tests.
 *
 * <p>
 * It is also the in-memory {@link DumClassificationStore} (D107), the default a
 * {@link DumClassificationEnricher} uses when no persistent store is wired: answers and calls last as
 * long as the process.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumEnrichmentCache implements DumClassificationStore {

    /** A cached entry; a {@code null} label is a cached "no label" (negative) result. */
    private record Entry(String label, long classifiedAt, long expiresAt) {}

    /** Calls older than this are dropped when a new one is recorded: no cap looks further back. */
    private static final Duration CALL_RETENTION = Duration.ofDays(2);

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Map<String, Queue<Instant>> calls = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final LongSupplier clock;

    public DumEnrichmentCache(long ttlMillis) {
        this(ttlMillis, System::currentTimeMillis);
    }

    public DumEnrichmentCache(long ttlMillis, LongSupplier clock) {
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    /**
     * Return the cached label for {@code key} if present and unexpired, otherwise invoke
     * {@code loader} exactly once, cache its result (including a "no label" outcome) and return it.
     */
    public Optional<String> resolve(String key, Supplier<Optional<String>> loader) {
        Entry cached = valid(key);
        if (cached != null) {
            return Optional.ofNullable(cached.label());
        }
        Optional<String> loaded = loader.get();
        long now = clock.getAsLong();
        store.put(key, new Entry(loaded.orElse(null), now, expiry(now)));
        return loaded;
    }

    @Override
    public Optional<Answer> find(String key) {
        Entry cached = valid(key);
        return cached == null ? Optional.empty()
                : Optional.of(new Answer(cached.label(), Instant.ofEpochMilli(cached.classifiedAt())));
    }

    @Override
    public void put(String key, Answer answer) {
        long now = clock.getAsLong();
        long classifiedAt = answer.classifiedAt() != null ? answer.classifiedAt().toEpochMilli() : now;
        store.put(key, new Entry(answer.label(), classifiedAt, expiry(now)));
    }

    @Override
    public long callsSince(String scope, Instant since) {
        Queue<Instant> made = calls.get(scope);
        return made == null ? 0 : made.stream().filter(at -> at.isAfter(since)).count();
    }

    @Override
    public void recordCall(String scope, Instant at) {
        Queue<Instant> made = calls.computeIfAbsent(scope, s -> new ConcurrentLinkedQueue<>());
        Instant oldest = at.minus(CALL_RETENTION);
        made.removeIf(call -> call.isBefore(oldest));
        made.add(at);
    }

    public void clear() {
        store.clear();
        calls.clear();
    }

    private Entry valid(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (ttlMillis > 0 && clock.getAsLong() > entry.expiresAt()) {
            store.remove(key, entry);
            return null;
        }
        return entry;
    }

    private long expiry(long now) {
        return ttlMillis > 0 ? now + ttlMillis : Long.MAX_VALUE;
    }
}
