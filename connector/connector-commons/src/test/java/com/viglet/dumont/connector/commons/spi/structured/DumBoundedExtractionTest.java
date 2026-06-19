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
                rec -> consumed.add(rec.id()));

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
                rec -> consumed.add(rec.id()));
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

        DumBoundedExtraction.extractEach(source, source.discover(), 8, rec -> {
            int now = inConsumer.incrementAndGet();
            maxInConsumer.accumulateAndGet(now, Math::max);
            consumed.add(rec.id());
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
                rec -> consumed.add(rec.id()));

        assertEquals(4, consumed.size(), "the empty and the failing id are both dropped");
        assertFalse(consumed.contains("id2"));
        assertFalse(consumed.contains("id4"));
    }

    @Test
    @Timeout(10)
    void perExtractTimeoutCancelsHungExtractionAndCountsItFailed() {
        // id3 hangs far past the timeout; the rest are instant. The hung extraction must be cancelled
        // and counted as failed, the run must still complete, and every other id must be consumed.
        SlowSource source = new SlowSource(ids(6), 0);
        source.hangFor("id3");
        List<String> consumed = new ArrayList<>();

        DumExtractionOutcome outcome = DumBoundedExtraction.extractEach(source, source.discover(), 4,
                200L, rec -> consumed.add(rec.id()));

        assertTrue(outcome.completed(), "a run with a cancelled extraction still completes");
        assertEquals(6, outcome.processed(), "the hung id is processed (as a failure), not skipped");
        assertEquals(1, outcome.failed(), "exactly the hung extraction is counted as failed");
        assertEquals(5, consumed.size(), "every non-hung id reaches the consumer");
        assertFalse(consumed.contains("id3"), "the hung id yields no record");
    }

    @Test
    @Timeout(10)
    void timeoutBoundsAHungExtractionEvenAtConcurrencyOne() {
        // With a timeout configured the run uses the pool even at concurrency 1, so a single hung
        // extraction cannot stall the whole run.
        SlowSource source = new SlowSource(ids(3), 0);
        source.hangFor("id1");
        List<String> consumed = new ArrayList<>();

        DumExtractionOutcome outcome = DumBoundedExtraction.extractEach(source, source.discover(), 1,
                200L, rec -> consumed.add(rec.id()));

        assertTrue(outcome.completed());
        assertEquals(1, outcome.failed(), "the hung id is the only failure");
        assertEquals(List.of("id0", "id2"), consumed.stream().sorted().toList(),
                "the two fast ids are consumed, the hung one dropped");
    }

    @Test
    @Timeout(10)
    void failureWaveRatioIsReported() {
        // A wave of throwing extractions is counted so the plugin can finish such a run standalone.
        SlowSource source = new SlowSource(ids(4), 0);
        source.failFor("id0");
        source.failFor("id1");
        source.failFor("id2");
        List<String> consumed = new ArrayList<>();

        DumExtractionOutcome outcome = DumBoundedExtraction.extractEach(source, source.discover(), 2,
                rec -> consumed.add(rec.id()));

        assertTrue(outcome.completed());
        assertEquals(3, outcome.failed(), "all three throwing extractions are counted as failed");
        assertEquals(0.75, outcome.failureRatio(), 1e-9, "failed / processed");
        assertEquals(1, consumed.size(), "only the one good id is published");
    }

    @Test
    void sequentialRunCountsFailuresButNotEmpties() {
        // Empties are a legitimate "no record here" and must not be held against the run (D34).
        SlowSource source = new SlowSource(ids(5), 0);
        source.failFor("id1");
        source.emptyFor("id2");

        DumExtractionOutcome outcome =
                DumBoundedExtraction.extractEach(source, source.discover(), 1, rec -> {
                });

        assertEquals(1, outcome.failed(), "only the throwing id is a failure, the empty one is not");
        assertEquals(5, outcome.processed());
        assertEquals(0.2, outcome.failureRatio(), 1e-9);
    }

    @Test
    void emptyIdListIsANoOp() {
        SlowSource source = new SlowSource(List.of(), 0);
        DumExtractionOutcome outcome =
                DumBoundedExtraction.extractEach(source, source.discover(), 4, rec -> {
                    throw new AssertionError("consumer must not run for an empty id list");
                });
        assertTrue(outcome.completed(), "an empty run is complete, not interrupted");
        assertEquals(0, outcome.discovered());
    }

    @Test
    void completeRunReportsCompleted() {
        SlowSource source = new SlowSource(ids(5), 0);

        DumExtractionOutcome sequential =
                DumBoundedExtraction.extractEach(source, source.discover(), 1, rec -> {
                });
        DumExtractionOutcome parallel =
                DumBoundedExtraction.extractEach(source, source.discover(), 4, rec -> {
                });

        assertTrue(sequential.completed(), "a sequential run that visits every id is complete");
        assertEquals(5, sequential.processed());
        assertTrue(parallel.completed(), "a parallel run that visits every id is complete");
        assertEquals(5, parallel.processed());
    }

    @Test
    @Timeout(10)
    void interruptedSequentialRunReportsInterrupted() {
        // The calling thread is interrupted partway; the sequential loop must bail and report the
        // partial run (roadmap D33) so the caller can skip the destructive deindex sweep.
        SlowSource source = new SlowSource(ids(6), 0);
        List<String> consumed = new ArrayList<>();

        DumExtractionOutcome outcome =
                DumBoundedExtraction.extractEach(source, source.discover(), 1, rec -> {
                    consumed.add(rec.id());
                    if (consumed.size() == 2) {
                        Thread.currentThread().interrupt();
                    }
                });

        try {
            assertFalse(outcome.completed(), "an interrupted run is not complete");
            assertTrue(outcome.interrupted());
            assertEquals(2, outcome.processed(), "stopped right after the interrupt was observed");
            assertEquals(6, outcome.discovered());
        } finally {
            Thread.interrupted(); // clear the flag so it cannot leak into another test
        }
    }

    @Test
    @Timeout(10)
    void interruptedParallelRunReportsInterrupted() {
        SlowSource source = new SlowSource(ids(12), 0);
        List<String> consumed = new ArrayList<>();

        DumExtractionOutcome outcome =
                DumBoundedExtraction.extractEach(source, source.discover(), 4, rec -> {
                    consumed.add(rec.id());
                    if (consumed.size() == 1) {
                        Thread.currentThread().interrupt();
                    }
                });

        try {
            assertFalse(outcome.completed(), "an interrupted parallel run is not complete");
            assertTrue(outcome.interrupted());
            assertTrue(outcome.processed() < 12, "the run stopped before visiting every id");
        } finally {
            Thread.interrupted(); // clear the flag so it cannot leak into another test
        }
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
        private final List<String> hangIds = new ArrayList<>();

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

        /** Make {@code extract(id)} block (interruptibly) far past any per-extract timeout. */
        void hangFor(String id) {
            hangIds.add(id);
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
                if (hangIds.contains(contentId)) {
                    Thread.sleep(10_000); // far past any test timeout; cancelled via interrupt
                } else if (sleepMs > 0) {
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
