/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coverage for roadmap D06 — the effective per-request delay: the larger of the per-source
 * configured delay and the {@code robots.txt} {@code Crawl-delay}, with the advertised delay capped
 * so a hostile directive cannot stall a crawl.
 */
@DisplayName("DumWCPluginProcess.effectiveDelayMs (D06)")
class DumWCPluginProcessDelayTest {

    private static final long CAP = 30_000L;

    @Test
    @DisplayName("No configured delay and no advertised delay → no spacing")
    void noDelay() {
        assertEquals(0L, DumWCPluginProcess.effectiveDelayMs(null, -1L, CAP));
        assertEquals(0L, DumWCPluginProcess.effectiveDelayMs(0, -1L, CAP));
    }

    @Test
    @DisplayName("Configured delay applies when no robots delay is advertised")
    void configuredOnly() {
        assertEquals(1500L, DumWCPluginProcess.effectiveDelayMs(1500, -1L, CAP));
    }

    @Test
    @DisplayName("The larger of configured and advertised wins")
    void takesMax() {
        assertEquals(5000L, DumWCPluginProcess.effectiveDelayMs(2000, 5000L, CAP));
        assertEquals(4000L, DumWCPluginProcess.effectiveDelayMs(4000, 1000L, CAP));
    }

    @Test
    @DisplayName("An advertised crawl-delay above the cap is clamped to the cap")
    void capsAdvertisedDelay() {
        assertEquals(CAP, DumWCPluginProcess.effectiveDelayMs(null, 120_000L, CAP));
    }

    @Test
    @DisplayName("The cap does not clamp the per-source configured delay")
    void capDoesNotClampConfigured() {
        assertEquals(60_000L, DumWCPluginProcess.effectiveDelayMs(60_000, 5000L, CAP));
    }
}
