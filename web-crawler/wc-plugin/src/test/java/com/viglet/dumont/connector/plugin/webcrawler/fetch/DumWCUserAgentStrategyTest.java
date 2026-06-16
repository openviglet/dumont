/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler.fetch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.webcrawler.commons.fetch.DumWCUserAgentStrategy;

/**
 * Coverage for roadmap D07 — user-agent strategy parsing: absent/blank/invalid config keeps the
 * historical per-source agent; {@code PER_REQUEST} opts into rotation.
 */
@DisplayName("DumWCUserAgentStrategy.fromConfig (D07)")
class DumWCUserAgentStrategyTest {

    @Test
    @DisplayName("Null, blank and unknown values default to PER_SOURCE")
    void defaultsToPerSource() {
        assertEquals(DumWCUserAgentStrategy.PER_SOURCE, DumWCUserAgentStrategy.fromConfig(null));
        assertEquals(DumWCUserAgentStrategy.PER_SOURCE, DumWCUserAgentStrategy.fromConfig("  "));
        assertEquals(DumWCUserAgentStrategy.PER_SOURCE, DumWCUserAgentStrategy.fromConfig("nonsense"));
    }

    @Test
    @DisplayName("Known values parse case- and whitespace-insensitively")
    void parsesKnownValues() {
        assertEquals(DumWCUserAgentStrategy.PER_REQUEST, DumWCUserAgentStrategy.fromConfig("per_request"));
        assertEquals(DumWCUserAgentStrategy.PER_REQUEST, DumWCUserAgentStrategy.fromConfig("  PER_REQUEST "));
        assertEquals(DumWCUserAgentStrategy.PER_SOURCE, DumWCUserAgentStrategy.fromConfig("Per_Source"));
    }
}
