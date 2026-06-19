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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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
 * <p>
 * <strong>Partial-run safety (roadmap D33).</strong> {@code extractEach} returns a
 * {@link DumExtractionOutcome} telling the caller whether the run got through its whole discovered id
 * set or was cut short by an interrupt (pool/JVM shutdown). The structured loop uses this to skip the
 * destructive deindex sweep on an interrupted run, so a transient interruption cannot wipe a still-
 * valid index.
 *
 * <p>
 * <strong>Per-extract timeout + failure count (roadmap D34).</strong> A single {@code extract(id)}
 * that hangs past the HTTP client's own timeout (a stalled {@code infinity.json} fetch) would
 * otherwise stall the whole run on the unbounded {@code completion.take()}. An optional
 * {@code perExtractTimeoutMs} (off by default) bounds each in-flight extraction: an overrun is
 * cancelled and counted as <em>failed</em>, never as a record. The returned outcome carries the
 * failed count so the plugin can finish a whole <em>wave</em> of failures standalone (D34's
 * failure-wave guard) instead of mass-deindexing what a transient outage made un-fetchable. When a
 * timeout is configured the run always uses the pool — even at {@code concurrency <= 1} — since a
 * bound cannot be enforced on an inline call.
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
     * Extract every content id and feed each successfully-extracted record to {@code consumer}, with
     * no per-extract timeout (the historical 4-arg contract; delegates with the timeout disabled).
     *
     * @param source      the structured source to extract from
     * @param contentIds  the ids to extract (typically {@link DumStructuredSource#discover()})
     * @param concurrency the maximum number of concurrent {@code extract(id)} calls; {@code <= 1}
     *                    runs sequentially on the calling thread (the unchanged default)
     * @param consumer    invoked serially, on the calling thread, once per extracted record
     * @return whether the run got through its whole discovered id set or was interrupted partway
     *         (roadmap <strong>D33</strong>) — the caller uses this to decide whether the
     *         tracking-driven deindex sweep is safe to run
     */
    public static DumExtractionOutcome extractEach(DumStructuredSource source, List<String> contentIds,
            int concurrency, Consumer<DumStructuredRecord> consumer) {
        return extractEach(source, contentIds, concurrency, 0L, consumer);
    }

    /**
     * Extract every content id and feed each successfully-extracted record to {@code consumer},
     * bounding each in-flight extraction by {@code perExtractTimeoutMs} (roadmap <strong>D34</strong>).
     *
     * @param source              the structured source to extract from
     * @param contentIds          the ids to extract (typically {@link DumStructuredSource#discover()})
     * @param concurrency         the maximum number of concurrent {@code extract(id)} calls; {@code
     *                            <= 1} runs sequentially unless a timeout forces the pool
     * @param perExtractTimeoutMs per-extraction wall-clock bound in milliseconds; {@code <= 0}
     *                            disables it (no extraction is ever cancelled for time)
     * @param consumer            invoked serially, on the calling thread, once per extracted record
     * @return the run outcome — completed-vs-interrupted (D33) plus the failed-extraction count (D34)
     */
    public static DumExtractionOutcome extractEach(DumStructuredSource source, List<String> contentIds,
            int concurrency, long perExtractTimeoutMs, Consumer<DumStructuredRecord> consumer) {
        if (contentIds == null || contentIds.isEmpty()) {
            return DumExtractionOutcome.completed(0);
        }
        boolean timed = perExtractTimeoutMs > 0;
        // Without a timeout, a single id or concurrency <= 1 runs inline (the unchanged default). With
        // a timeout configured we always use the pool — even at one thread — because a per-extract
        // bound cannot be enforced on an inline call (D34).
        if (!timed && (concurrency <= 1 || contentIds.size() == 1)) {
            return runSequential(source, contentIds, consumer);
        }
        int threads = Math.clamp(concurrency, 1, contentIds.size());
        return runParallel(source, contentIds, threads, perExtractTimeoutMs, consumer);
    }

    private static DumExtractionOutcome runSequential(DumStructuredSource source,
            List<String> contentIds, Consumer<DumStructuredRecord> consumer) {
        int total = contentIds.size();
        int processed = 0;
        int failed = 0;
        for (String id : contentIds) {
            // Bail out if the calling thread has been interrupted (pool/JVM shutdown) so a partial
            // run is reported as such instead of silently driving the destructive deindex sweep.
            if (Thread.currentThread().isInterrupted()) {
                return DumExtractionOutcome.interrupted(total, processed, failed);
            }
            Extraction extraction = extractOne(source, id);
            if (extraction.failed()) {
                failed++;
            }
            if (extraction.present()) {
                consumer.accept(extraction.rec());
            }
            processed++;
        }
        return DumExtractionOutcome.completed(total, failed);
    }

    private static DumExtractionOutcome runParallel(DumStructuredSource source,
            List<String> contentIds, int threads, long timeoutMs, Consumer<DumStructuredRecord> consumer) {
        int total = contentIds.size();
        int processed = 0;
        int failed = 0;
        boolean interrupted = false;
        boolean timed = timeoutMs > 0;
        ExecutorService pool = Executors.newFixedThreadPool(threads, daemonFactory());
        try {
            ExecutorCompletionService<Extraction> completion = new ExecutorCompletionService<>(pool);
            // In-flight futures and (when a timeout is set) their per-extract deadlines, both keyed by
            // the Future returned by submit — the same object the completion service hands back.
            Set<Future<Extraction>> live = new LinkedHashSet<>();
            Map<Future<Extraction>, Long> deadlines = new HashMap<>();
            Iterator<String> ids = contentIds.iterator();
            // Prime the pool: keep at most `threads` extractions in flight so completed-but-unconsumed
            // records cannot pile up unbounded while the (serial) consumer catches up.
            while (ids.hasNext() && live.size() < threads) {
                submit(completion, source, ids.next(), live, deadlines, timeoutMs);
            }
            while (!live.isEmpty()) {
                Future<Extraction> done;
                try {
                    done = timed ? completion.poll(pollWaitMs(deadlines), TimeUnit.MILLISECONDS)
                            : completion.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interrupted = true;
                    break;
                }
                if (done == null) {
                    // The poll window elapsed: cancel every extraction past its deadline. Each is one
                    // visited id that failed, so it counts toward both processed and failed (D34).
                    int expired = cancelExpired(live, deadlines, source, timeoutMs, ids, completion);
                    failed += expired;
                    processed += expired;
                    continue;
                }
                if (!live.remove(done)) {
                    // A previously-cancelled extraction draining from the completion queue — already
                    // accounted for when it timed out; ignore it.
                    continue;
                }
                deadlines.remove(done);
                Extraction extraction = consume(done, source);
                if (extraction.failed()) {
                    failed++;
                }
                if (extraction.present()) {
                    consumer.accept(extraction.rec());
                }
                processed++;
                if (ids.hasNext()) {
                    submit(completion, source, ids.next(), live, deadlines, timeoutMs);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return interrupted ? DumExtractionOutcome.interrupted(total, processed, failed)
                : DumExtractionOutcome.completed(total, failed);
    }

    private static void submit(ExecutorCompletionService<Extraction> completion,
            DumStructuredSource source, String contentId, Set<Future<Extraction>> live,
            Map<Future<Extraction>, Long> deadlines, long timeoutMs) {
        Future<Extraction> future = completion.submit(() -> extractOne(source, contentId));
        live.add(future);
        if (timeoutMs > 0) {
            deadlines.put(future, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs));
        }
    }

    /** Milliseconds to wait for the next completion: time until the earliest deadline, floored at 1. */
    private static long pollWaitMs(Map<Future<Extraction>, Long> deadlines) {
        long now = System.nanoTime();
        long earliest = Long.MAX_VALUE;
        for (long deadline : deadlines.values()) {
            earliest = Math.min(earliest, deadline);
        }
        if (earliest == Long.MAX_VALUE) {
            return 1L;
        }
        return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(earliest - now));
    }

    /**
     * Cancel every still-running extraction whose deadline has passed, drop it from the live set, and
     * submit a replacement so the pool stays busy. The cancelled future still surfaces later from the
     * completion queue, where {@code live.remove} returns false and it is ignored. An extraction that
     * <em>already completed</em> by the time its deadline is checked is left alone — the main loop
     * drains and consumes it normally on the next poll, so a record that finished right at the
     * deadline is never miscounted as a timeout. Returns the number cancelled (each counts as one
     * failed and one processed id).
     */
    private static int cancelExpired(Set<Future<Extraction>> live,
            Map<Future<Extraction>, Long> deadlines, DumStructuredSource source, long timeoutMs,
            Iterator<String> ids, ExecutorCompletionService<Extraction> completion) {
        long now = System.nanoTime();
        int cancelled = 0;
        Iterator<Map.Entry<Future<Extraction>, Long>> it = deadlines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Future<Extraction>, Long> entry = it.next();
            Future<Extraction> expired = entry.getKey();
            if (entry.getValue() > now || expired.isDone()) {
                continue;
            }
            expired.cancel(true);
            it.remove();
            live.remove(expired);
            cancelled++;
            log.warn("Extraction timed out after {} ms in source '{}' — counted as failed", timeoutMs,
                    source.getSourceName());
            if (ids.hasNext()) {
                submit(completion, source, ids.next(), live, deadlines, timeoutMs);
            }
        }
        return cancelled;
    }

    private static Extraction consume(Future<Extraction> done, DumStructuredSource source) {
        try {
            return done.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Extraction.failure();
        } catch (CancellationException e) {
            return Extraction.failure();
        } catch (ExecutionException e) {
            // extractOne already swallows RuntimeException, so this is an unexpected escape.
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("Structured extraction task failed in source '{}': {}", source.getSourceName(),
                    cause.toString());
            return Extraction.failure();
        }
    }

    private static Extraction extractOne(DumStructuredSource source, String contentId) {
        try {
            Optional<DumStructuredRecord> rec = source.extract(contentId);
            if (rec.isEmpty()) {
                log.warn("No record extracted for id '{}' in source '{}'", contentId,
                        source.getSourceName());
                return Extraction.empty();
            }
            return Extraction.of(rec.get());
        } catch (RuntimeException e) {
            log.warn("Extraction failed for id '{}' in source '{}': {}", contentId,
                    source.getSourceName(), e.toString());
            return Extraction.failure();
        }
    }

    /**
     * One {@code extract(id)} attempt: a present record, a legitimate empty result, or a failure (the
     * extraction threw or was cancelled by the per-extract timeout). Only failures count toward the
     * failure-wave ratio (D34); empties are dropped but not held against the run.
     */
    private record Extraction(DumStructuredRecord rec, boolean failed) {
        static Extraction of(DumStructuredRecord rec) {
            return new Extraction(rec, false);
        }

        static Extraction empty() {
            return new Extraction(null, false);
        }

        static Extraction failure() {
            return new Extraction(null, true);
        }

        boolean present() {
            return rec != null;
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
