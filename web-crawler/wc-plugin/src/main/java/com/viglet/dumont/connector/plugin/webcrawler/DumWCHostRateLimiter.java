/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-host politeness gate (roadmap D08). Serializes requests to the same host so consecutive
 * fetches are separated by at least the effective delay floor, while letting different hosts
 * proceed in parallel — so a bounded pool of crawl workers stays concurrent without violating a
 * host's rate limit.
 *
 * <p>Reservation is pure time arithmetic: {@link #reserve} hands back the absolute instant at which
 * the request may proceed and the caller sleeps for the difference. Keeping the wait outside the
 * lock means the spacing math is unit-testable without real clocks or threads.
 */
class DumWCHostRateLimiter {

    private final ConcurrentMap<String, Long> nextAllowedMs = new ConcurrentHashMap<>();

    /**
     * Reserve the next slot for {@code host}: returns the absolute timestamp (ms) at which the
     * request may proceed — never earlier than {@code nowMs} and never within {@code delayMs} of the
     * slot previously handed out for the same host. The slot is claimed atomically, so concurrent
     * workers hitting the same host queue up behind one another instead of colliding. A
     * non-positive {@code delayMs} imposes no spacing and returns {@code nowMs}.
     */
    long reserve(String host, long delayMs, long nowMs) {
        if (delayMs <= 0) {
            return nowMs;
        }
        long[] start = new long[1];
        nextAllowedMs.compute(host == null ? "" : host, (h, previousSlot) -> {
            long slot = previousSlot == null ? nowMs : Math.max(nowMs, previousSlot);
            start[0] = slot;
            return slot + delayMs;
        });
        return start[0];
    }
}
