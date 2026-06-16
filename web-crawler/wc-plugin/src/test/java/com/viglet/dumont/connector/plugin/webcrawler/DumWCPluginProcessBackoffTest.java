/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coverage for roadmap D07 — adaptive back-off: the delay grows exponentially per retried attempt
 * and is clamped to the configured ceiling so a persistently throttling host cannot stall a crawl.
 */
@DisplayName("DumWCPluginProcess.backoffDelayMs (D07)")
class DumWCPluginProcessBackoffTest {

    private static final long BASE = 1000L;
    private static final long MAX = 30_000L;

    @Test
    @DisplayName("Delay doubles each attempt")
    void doublesPerAttempt() {
        assertEquals(1000L, DumWCPluginProcess.backoffDelayMs(0, BASE, MAX));
        assertEquals(2000L, DumWCPluginProcess.backoffDelayMs(1, BASE, MAX));
        assertEquals(4000L, DumWCPluginProcess.backoffDelayMs(2, BASE, MAX));
        assertEquals(8000L, DumWCPluginProcess.backoffDelayMs(3, BASE, MAX));
    }

    @Test
    @DisplayName("Delay is clamped to the configured maximum")
    void clampsToMax() {
        assertEquals(MAX, DumWCPluginProcess.backoffDelayMs(5, BASE, MAX));
        assertEquals(MAX, DumWCPluginProcess.backoffDelayMs(40, BASE, MAX));
    }

    @Test
    @DisplayName("A non-positive base disables back-off")
    void zeroBaseDisables() {
        assertEquals(0L, DumWCPluginProcess.backoffDelayMs(3, 0L, MAX));
    }
}
