/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Minimal {@code robots.txt} model (roadmap D06): the {@code Sitemap:} directives and the
 * {@code Crawl-delay} that apply to a given user-agent.
 *
 * <p>
 * Parsing follows the de-facto conventions: {@code Sitemap:} lines are non-group (they apply to the
 * whole file regardless of the surrounding {@code User-agent} block), while {@code Crawl-delay} is
 * scoped to a group of one or more {@code User-agent} lines. A delay declared for a group whose agent
 * token matches the crawler's user-agent wins over one declared for the wildcard {@code *} group.
 * {@code Crawl-delay} is advertised in seconds (fractional values allowed) and normalized to
 * milliseconds here.
 *
 * @param sitemaps     absolute sitemap URLs advertised in the file, in declaration order
 * @param crawlDelayMs the applicable crawl delay in milliseconds, or {@code -1} when none applies
 */
record DumWCRobotsTxt(List<String> sitemaps, long crawlDelayMs) {

    static final DumWCRobotsTxt EMPTY = new DumWCRobotsTxt(List.of(), -1L);

    private static final String USER_AGENT = "user-agent";
    private static final String SITEMAP = "sitemap";
    private static final String CRAWL_DELAY = "crawl-delay";
    private static final String WILDCARD = "*";

    static DumWCRobotsTxt parse(String content, String userAgent) {
        if (StringUtils.isBlank(content)) {
            return EMPTY;
        }
        String agent = userAgent == null ? "" : userAgent.toLowerCase();
        List<String> sitemaps = new ArrayList<>();
        Set<String> currentAgents = new LinkedHashSet<>();
        long wildcardDelayMs = -1L;
        long matchedDelayMs = -1L;
        boolean lastWasDirective = false;
        for (String rawLine : content.split("\\R")) {
            String line = stripComment(rawLine).trim();
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            if (value.isEmpty()) {
                continue;
            }
            switch (key) {
                case USER_AGENT -> {
                    if (lastWasDirective) {
                        currentAgents.clear();
                    }
                    currentAgents.add(value.toLowerCase());
                    lastWasDirective = false;
                }
                case SITEMAP -> sitemaps.add(value); // non-group: does not affect grouping
                case CRAWL_DELAY -> {
                    long ms = parseDelayMs(value);
                    if (ms >= 0) {
                        for (String groupAgent : currentAgents) {
                            if (WILDCARD.equals(groupAgent)) {
                                wildcardDelayMs = Math.max(wildcardDelayMs, ms);
                            } else if (agent.contains(groupAgent)) {
                                matchedDelayMs = Math.max(matchedDelayMs, ms);
                            }
                        }
                    }
                    lastWasDirective = true;
                }
                default -> lastWasDirective = true; // allow / disallow / unknown
            }
        }
        long crawlDelayMs = matchedDelayMs >= 0 ? matchedDelayMs : wildcardDelayMs;
        return new DumWCRobotsTxt(List.copyOf(sitemaps), crawlDelayMs);
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private static long parseDelayMs(String value) {
        try {
            double seconds = Double.parseDouble(value);
            return seconds >= 0 ? Math.round(seconds * 1000) : -1L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
