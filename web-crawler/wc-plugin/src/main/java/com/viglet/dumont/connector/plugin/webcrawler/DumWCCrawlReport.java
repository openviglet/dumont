/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Per-source crawl outcome accumulator (roadmap D09). Every page fetch contributes either an indexed
 * item or a typed failure, so a finished crawl can answer the two questions the old silent drain
 * could not: did this run produce anything indexable, and — when it did not — why.
 *
 * <p>
 * The {@link #isTotalFailure() total-failure} signal drives the <em>safe-run guard</em>: a run that
 * indexed nothing must not deindex the previously indexed data (porting cursarei {@code engine.py}'s
 * guard), since a transient outage or a fresh block must not wipe a good index. The
 * {@link #summary()} renders the per-source failure report (status codes, blocks, dead URLs) so an
 * empty run is never silent.
 *
 * <p>
 * Thread-safe: the bounded worker pool (roadmap D08) records into it concurrently.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
final class DumWCCrawlReport {

    /**
     * A single failed page fetch. {@code statusCode} is {@code 0} when no HTTP status was returned —
     * a dead URL whose DNS/connect/read failed before a response line.
     */
    record Failure(String url, int statusCode, String reason) {
        boolean blocked() {
            return statusCode == 403 || statusCode == 429;
        }

        boolean dead() {
            return statusCode == 0 || statusCode == 404;
        }
    }

    private final AtomicInteger indexed = new AtomicInteger();
    // D12: pages revalidated as unchanged (HTTP 304 / sitemap lastmod) — fetched cheaply or not at all,
    // re-emitted as unchanged so they survive the deindex pass. A healthy outcome, not a failure.
    private final AtomicInteger notModified = new AtomicInteger();
    private final Queue<Failure> failures = new ConcurrentLinkedQueue<>();

    /** Count one item the crawl produced for indexing. */
    void recordIndexed() {
        indexed.incrementAndGet();
    }

    /** Count one page revalidated as unchanged (roadmap D12): no re-index, but a successful outcome. */
    void recordNotModified() {
        notModified.incrementAndGet();
    }

    /** Record a failed fetch; {@code statusCode} is {@code 0} for a dead URL with no HTTP response. */
    void recordFailure(String url, int statusCode, String reason) {
        failures.add(new Failure(url, statusCode, reason));
    }

    int indexedCount() {
        return indexed.get();
    }

    int notModifiedCount() {
        return notModified.get();
    }

    int failureCount() {
        return failures.size();
    }

    List<Failure> failures() {
        return List.copyOf(failures);
    }

    /**
     * No successful outcome at all — neither a fresh index nor an unchanged revalidation (roadmap
     * D12). This is the trigger for the safe-run guard: a run that only failed (or did nothing) must
     * not deindex the previously indexed data. A re-run where every page came back {@code 304} is
     * <em>not</em> a total failure — the pages were verified.
     */
    boolean isTotalFailure() {
        return indexed.get() == 0 && notModified.get() == 0;
    }

    /**
     * The per-source failure report: indexed and unchanged counts, total failures, a block/dead/other
     * breakdown and a count by HTTP status (status {@code 0} = dead URL with no response).
     */
    String summary() {
        long blocked = failures.stream().filter(Failure::blocked).count();
        long dead = failures.stream().filter(Failure::dead).count();
        long other = failureCount() - blocked - dead;
        Map<Integer, Long> byStatus = failures.stream()
                .collect(Collectors.groupingBy(Failure::statusCode, Collectors.counting()));
        return "indexed=%d, unchanged=%d, failures=%d (blocked=%d, dead=%d, other=%d), byStatus=%s"
                .formatted(indexedCount(), notModifiedCount(), failureCount(), blocked, dead, other,
                        byStatus);
    }
}
