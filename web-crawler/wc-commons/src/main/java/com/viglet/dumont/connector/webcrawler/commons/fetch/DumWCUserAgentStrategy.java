/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.webcrawler.commons.fetch;

import java.util.Locale;

/**
 * How a crawl chooses the {@code User-Agent} it presents (roadmap D07).
 *
 * <ul>
 * <li>{@link #PER_SOURCE} — one user agent for the whole crawl (the default and historical
 * behavior): polite and cache-friendly.</li>
 * <li>{@link #PER_REQUEST} — a fresh user agent on every request: rotates the fingerprint on
 * sources that rate-limit or block a fixed agent.</li>
 * </ul>
 */
public enum DumWCUserAgentStrategy {

    PER_SOURCE,
    PER_REQUEST;

    /** Parse a source's configured strategy, defaulting to {@link #PER_SOURCE} when absent/invalid. */
    public static DumWCUserAgentStrategy fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return PER_SOURCE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PER_SOURCE;
        }
    }
}
