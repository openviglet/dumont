/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coverage for roadmap D06 — {@code robots.txt} parsing. Pins the two facts the crawler relies on:
 * the {@code Sitemap:} entry points (non-group, file-wide) and the {@code Crawl-delay} that applies
 * to the crawler's user-agent (specific group wins over the wildcard {@code *}).
 */
@DisplayName("DumWCRobotsTxt parsing (D06)")
class DumWCRobotsTxtTest {

    private static final String UA = "Mozilla/5.0 (compatible; DumontBot/1.0)";

    @Test
    @DisplayName("Sitemap directives are collected regardless of the surrounding group")
    void collectsSitemaps() {
        String robots = """
                User-agent: *
                Disallow: /private
                Sitemap: https://example.com/sitemap.xml
                Sitemap: https://example.com/news-sitemap.xml
                """;

        DumWCRobotsTxt parsed = DumWCRobotsTxt.parse(robots, UA);

        assertEquals(List.of("https://example.com/sitemap.xml",
                "https://example.com/news-sitemap.xml"), parsed.sitemaps());
    }

    @Test
    @DisplayName("Crawl-delay in seconds is normalized to milliseconds")
    void crawlDelaySeconds() {
        String robots = """
                User-agent: *
                Crawl-delay: 2
                """;

        assertEquals(2000L, DumWCRobotsTxt.parse(robots, UA).crawlDelayMs());
    }

    @Test
    @DisplayName("Fractional crawl-delay is supported")
    void fractionalCrawlDelay() {
        String robots = """
                User-agent: *
                Crawl-delay: 0.5
                """;

        assertEquals(500L, DumWCRobotsTxt.parse(robots, UA).crawlDelayMs());
    }

    @Test
    @DisplayName("A group matching the user-agent wins over the wildcard group")
    void specificGroupWins() {
        String robots = """
                User-agent: *
                Crawl-delay: 10

                User-agent: DumontBot
                Crawl-delay: 3
                """;

        assertEquals(3000L, DumWCRobotsTxt.parse(robots, UA).crawlDelayMs());
    }

    @Test
    @DisplayName("Falls back to the wildcard group when no specific group matches")
    void wildcardFallback() {
        String robots = """
                User-agent: Googlebot
                Crawl-delay: 7

                User-agent: *
                Crawl-delay: 4
                """;

        assertEquals(4000L, DumWCRobotsTxt.parse(robots, UA).crawlDelayMs());
    }

    @Test
    @DisplayName("Comments and blank lines are ignored")
    void ignoresComments() {
        String robots = """
                # global rules
                User-agent: *
                Crawl-delay: 1  # be polite
                Sitemap: https://example.com/sitemap.xml # the index
                """;

        DumWCRobotsTxt parsed = DumWCRobotsTxt.parse(robots, UA);

        assertEquals(1000L, parsed.crawlDelayMs());
        assertEquals(List.of("https://example.com/sitemap.xml"), parsed.sitemaps());
    }

    @Test
    @DisplayName("Empty or absent input yields EMPTY (no sitemaps, no delay)")
    void emptyInput() {
        assertEquals(DumWCRobotsTxt.EMPTY, DumWCRobotsTxt.parse(null, UA));
        assertEquals(DumWCRobotsTxt.EMPTY, DumWCRobotsTxt.parse("   ", UA));
        DumWCRobotsTxt noDelay = DumWCRobotsTxt.parse("User-agent: *\nDisallow:\n", UA);
        assertTrue(noDelay.sitemaps().isEmpty());
        assertEquals(-1L, noDelay.crawlDelayMs());
    }
}
