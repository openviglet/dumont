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

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Bounded-concurrency extraction for the structured loop (roadmap <strong>D31</strong>).
 *
 * <p>
 * The structured loop ({@link DumStructuredSourcePlugin}) extracts ids one at a time. For an
 * HTTP-heavy source — AEM, where every {@link DumStructuredSource#extract(String) extract(id)} is a
 * single {@code infinity.json} fetch — a large site indexes markedly slower than the legacy reactive
 * navigator, which parallelised its tree traversal. This helper restores that parallelism at the
 * <em>framework</em> level so every adapter inherits it: {@code extract(id)} runs on a bounded thread
 * pool while the {@code consumer} (publish + snapshot / coverage / provenance capture) is invoked
 * <strong>serially on the calling thread</strong> as records arrive.
 *
 * <p>
 * Two properties make that safe and parity-preserving:
 * <ul>
 * <li><strong>Serial consume.</strong> Only extraction — the I/O-bound, side-effect-free stage — runs
 * in parallel. The consumer never runs concurrently, so the single-threaded snapshot / coverage /
 * provenance builders and the indexing session need no change.</li>
 * <li><strong>Order-independent capture.</strong> Records may complete out of submission order; that
 * is immaterial because every capture the consumer performs is keyed by record id, not position.</li>
 * </ul>
 *
 * <p>
 * With {@code concurrency <= 1} (the default for every adapter) extraction runs inline on the calling
 * thread exactly as before — no pool is created. With {@code concurrency > 1} at most {@code threads}
 * extractions are in flight at once, so memory stays bounded however far extraction races ahead of the
 * consumer. An extraction that throws or yields no record is logged and dropped, never fabricated.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumBoundedExtraction {

    private DumBoundedExtraction() {
        // utility
    }

    /**
     * Extract every content id and feed each successfully-extracted record to {@code consumer}.
     *
     * @param source      the structured source to extract from
     * @param contentIds  the ids to extract (typically {@link DumStructuredSource#discover()})
     * @param concurrency the maximum number of concurrent {@code extract(id)} calls; {@code <= 1}
     *                    runs sequentially on the calling thread (the unchanged default)
     * @param consumer    invoked serially, on the calling thread, once per extracted record
     */
    public static void extractEach(DumStructuredSource source, List<String> contentIds,
            int concurrency, Consumer<DumStructuredRecord> consumer) {
        if (contentIds == null || contentIds.isEmpty()) {
            return;
        }
        if (concurrency <= 1 || contentIds.size() == 1) {
            contentIds.forEach(id -> extractOne(source, id).ifPresent(consumer));
            return;
        }
        runParallel(source, contentIds, Math.min(concurrency, contentIds.size()), consumer);
    }

    private static void runParallel(DumStructuredSource source, List<String> contentIds, int threads,
            Consumer<DumStructuredRecord> consumer) {
        ExecutorService pool = Executors.newFixedThreadPool(threads, daemonFactory());
        try {
            ExecutorCompletionService<Optional<DumStructuredRecord>> completion =
                    new ExecutorCompletionService<>(pool);
            Iterator<String> ids = contentIds.iterator();
            // Prime the pool: keep at most `threads` extractions in flight so completed-but-unconsumed
            // records cannot pile up unbounded while the (serial) consumer catches up.
            int inFlight = 0;
            while (ids.hasNext() && inFlight < threads) {
                submit(completion, source, ids.next());
                inFlight++;
            }
            while (inFlight > 0) {
                Future<Optional<DumStructuredRecord>> done;
                try {
                    done = completion.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                inFlight--;
                consume(done, source, consumer);
                if (ids.hasNext()) {
                    submit(completion, source, ids.next());
                    inFlight++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void submit(ExecutorCompletionService<Optional<DumStructuredRecord>> completion,
            DumStructuredSource source, String contentId) {
        completion.submit(() -> extractOne(source, contentId));
    }

    private static void consume(Future<Optional<DumStructuredRecord>> done, DumStructuredSource source,
            Consumer<DumStructuredRecord> consumer) {
        try {
            done.get().ifPresent(consumer);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("Structured extraction task failed in source '{}': {}", source.getSourceName(),
                    cause.toString());
        }
    }

    private static Optional<DumStructuredRecord> extractOne(DumStructuredSource source,
            String contentId) {
        try {
            Optional<DumStructuredRecord> record = source.extract(contentId);
            if (record.isEmpty()) {
                log.warn("No record extracted for id '{}' in source '{}'", contentId,
                        source.getSourceName());
            }
            return record;
        } catch (RuntimeException e) {
            log.warn("Extraction failed for id '{}' in source '{}': {}", contentId,
                    source.getSourceName(), e.toString());
            return Optional.empty();
        }
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger seq = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "dum-extract-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
