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

package com.viglet.dumont.connector.commons.spi.structured;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Tests for bounded-concurrency extraction (roadmap D31): parallel extract, serial order-independent
 * consume, and grounding-safe handling of failed / empty extractions.
 */
class DumBoundedExtractionTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true).build())
            .build();

    @Test
    void sequentialByDefaultExtractsEveryRecord() {
        SlowSource source = new SlowSource(ids(5), 0);
        List<String> consumed = new ArrayList<>();

        DumBoundedExtraction.extractEach(source, source.discover(), 1,
                record -> consumed.add(record.id()));

        assertEquals(List.of("id0", "id1", "id2", "id3", "id4"), consumed,
                "sequential extraction preserves discovery order");
    }

    @Test
    @Timeout(10)
    void parallelExtractionOverlapsFetchesAndStillVisitsEveryRecord() {
        // 12 ids, each extract() sleeps 100ms. Sequentially that is >1.2s; with 6 threads it must
        // finish in a fraction of that — proving the fetches overlap.
        SlowSource source = new SlowSource(ids(12), 100);
        List<String> consumed = new ArrayList<>();

        long start = System.nanoTime();
        DumBoundedExtraction.extractEach(source, source.discover(), 6,
                record -> consumed.add(record.id()));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(12, consumed.size(), "every record reaches the consumer");
        assertEquals(12, source.maxObserved(), "all 12 ids were extracted");
        assertTrue(consumed.containsAll(ids(12)), "no id is dropped, regardless of completion order");
        assertTrue(elapsedMs < 1_000,
                "6-way parallel extraction must beat the ~1.2s sequential cost (was " + elapsedMs + "ms)");
        assertTrue(source.maxConcurrent() > 1, "extractions actually ran concurrently");
    }

    @Test
    @Timeout(10)
    void consumerIsInvokedSerially() {
        // The consumer accumulates into a non-thread-safe list and asserts it never runs re-entrantly;
        // a concurrency violation would surface as overlap > 1 or a lost element.
        SlowSource source = new SlowSource(ids(20), 5);
        AtomicInteger inConsumer = new AtomicInteger();
        AtomicInteger maxInConsumer = new AtomicInteger();
        List<String> consumed = new ArrayList<>();

        DumBoundedExtraction.extractEach(source, source.discover(), 8, record -> {
            int now = inConsumer.incrementAndGet();
            maxInConsumer.accumulateAndGet(now, Math::max);
            consumed.add(record.id());
            inConsumer.decrementAndGet();
        });

        assertEquals(1, maxInConsumer.get(), "consumer never runs concurrently");
        assertEquals(20, consumed.size(), "serial consume loses nothing");
    }

    @Test
    @Timeout(10)
    void emptyAndFailingExtractionsAreDroppedNotFabricated() {
        SlowSource source = new SlowSource(ids(6), 0);
        source.emptyFor("id2");
        source.failFor("id4");
        List<String> consumed = new ArrayList<>();

        DumBoundedExtraction.extractEach(source, source.discover(), 4,
                record -> consumed.add(record.id()));

        assertEquals(4, consumed.size(), "the empty and the failing id are both dropped");
        assertFalse(consumed.contains("id2"));
        assertFalse(consumed.contains("id4"));
    }

    @Test
    void emptyIdListIsANoOp() {
        SlowSource source = new SlowSource(List.of(), 0);
        DumBoundedExtraction.extractEach(source, source.discover(), 4, record -> {
            throw new AssertionError("consumer must not run for an empty id list");
        });
    }

    private static List<String> ids(int n) {
        return IntStream.range(0, n).mapToObj(i -> "id" + i).toList();
    }

    /** A structured source whose {@code extract} optionally sleeps and tracks observed concurrency. */
    private static final class SlowSource implements DumStructuredSource {
        private final List<String> ids;
        private final long sleepMs;
        private final AtomicInteger concurrent = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
        private final AtomicInteger observed = new AtomicInteger();
        private final List<String> emptyIds = new ArrayList<>();
        private final List<String> failIds = new ArrayList<>();

        SlowSource(List<String> ids, long sleepMs) {
            this.ids = ids;
            this.sleepMs = sleepMs;
        }

        void emptyFor(String id) {
            emptyIds.add(id);
        }

        void failFor(String id) {
            failIds.add(id);
        }

        int maxConcurrent() {
            return maxConcurrent.get();
        }

        int maxObserved() {
            return observed.get();
        }

        @Override
        public String getSourceName() {
            return "slow";
        }

        @Override
        public String getProviderName() {
            return "SLOW";
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of("Slow");
        }

        @Override
        public DumStructuredSchema schema() {
            return SCHEMA;
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }

        @Override
        public List<String> discover() {
            return ids;
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            observed.incrementAndGet();
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            try {
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
                }
                if (failIds.contains(contentId)) {
                    throw new IllegalStateException("boom for " + contentId);
                }
                if (emptyIds.contains(contentId)) {
                    return Optional.empty();
                }
                return Optional.of(DumStructuredRecord.builder(contentId, SCHEMA)
                        .set("title", "Title " + contentId)
                        .build());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } finally {
                concurrent.decrementAndGet();
            }
        }
    }
}
