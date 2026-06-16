/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coverage for roadmap D08 — the per-host politeness gate that keeps a bounded parallel crawl from
 * violating a host's rate floor. The reservation math is pure (the caller sleeps for the returned
 * gap), so spacing is asserted here without real clocks or threads.
 */
@DisplayName("DumWCHostRateLimiter (D08)")
class DumWCHostRateLimiterTest {

    private static final String HOST = "example.com";
    private static final long DELAY = 1000L;

    @Test
    @DisplayName("A non-positive delay imposes no spacing")
    void noDelay() {
        DumWCHostRateLimiter limiter = new DumWCHostRateLimiter();
        assertEquals(5000L, limiter.reserve(HOST, 0L, 5000L));
        assertEquals(5000L, limiter.reserve(HOST, -1L, 5000L));
    }

    @Test
    @DisplayName("Consecutive reservations on the same host are spaced by the delay floor")
    void spacesSameHost() {
        DumWCHostRateLimiter limiter = new DumWCHostRateLimiter();
        // Three workers reserve at the same instant: they queue up one delay apart.
        assertEquals(0L, limiter.reserve(HOST, DELAY, 0L));
        assertEquals(DELAY, limiter.reserve(HOST, DELAY, 0L));
        assertEquals(2 * DELAY, limiter.reserve(HOST, DELAY, 0L));
    }

    @Test
    @DisplayName("A request arriving after the reserved slot is not held back")
    void noArtificialWaitWhenIdle() {
        DumWCHostRateLimiter limiter = new DumWCHostRateLimiter();
        assertEquals(0L, limiter.reserve(HOST, DELAY, 0L));
        // 'now' is already past the previously reserved slot (0 + DELAY) → proceed immediately.
        assertEquals(5000L, limiter.reserve(HOST, DELAY, 5000L));
    }

    @Test
    @DisplayName("Different hosts never wait on one another")
    void independentHosts() {
        DumWCHostRateLimiter limiter = new DumWCHostRateLimiter();
        assertEquals(0L, limiter.reserve("a.example.com", DELAY, 0L));
        assertEquals(0L, limiter.reserve("b.example.com", DELAY, 0L));
        assertEquals(0L, limiter.reserve("c.example.com", DELAY, 0L));
    }
}
