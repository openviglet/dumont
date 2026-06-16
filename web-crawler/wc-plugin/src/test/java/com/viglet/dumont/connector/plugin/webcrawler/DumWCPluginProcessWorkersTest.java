/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coverage for roadmap D08 — the bounded crawl worker cap: a positive per-source override wins,
 * otherwise the {@code dumont.wc.workers} default applies, and the result is never below one worker.
 */
@DisplayName("DumWCPluginProcess.effectiveWorkers (D08)")
class DumWCPluginProcessWorkersTest {

    private static final int DEFAULT = 4;

    @Test
    @DisplayName("The default applies when no per-source override is set")
    void usesDefault() {
        assertEquals(DEFAULT, DumWCPluginProcess.effectiveWorkers(null, DEFAULT));
    }

    @Test
    @DisplayName("A non-positive override falls back to the default")
    void nonPositiveOverrideFallsBack() {
        assertEquals(DEFAULT, DumWCPluginProcess.effectiveWorkers(0, DEFAULT));
        assertEquals(DEFAULT, DumWCPluginProcess.effectiveWorkers(-2, DEFAULT));
    }

    @Test
    @DisplayName("A positive per-source override wins over the default")
    void overrideWins() {
        assertEquals(8, DumWCPluginProcess.effectiveWorkers(8, DEFAULT));
        assertEquals(1, DumWCPluginProcess.effectiveWorkers(1, DEFAULT));
    }

    @Test
    @DisplayName("The cap never drops below a single worker")
    void neverBelowOne() {
        assertEquals(1, DumWCPluginProcess.effectiveWorkers(null, 0));
        assertEquals(1, DumWCPluginProcess.effectiveWorkers(-1, -1));
    }
}
