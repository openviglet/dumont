/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coverage for roadmap D09 — the per-source crawl report: it counts indexable items, classifies
 * failures into blocks / dead URLs / other, and only flags a total failure when nothing was indexed
 * (the trigger for the safe-run guard that keeps the previously indexed data).
 */
@DisplayName("DumWCCrawlReport (D09)")
class DumWCCrawlReportTest {

    @Test
    @DisplayName("An empty run is a total failure")
    void emptyRunIsTotalFailure() {
        DumWCCrawlReport report = new DumWCCrawlReport();
        assertTrue(report.isTotalFailure());
        assertEquals(0, report.indexedCount());
    }

    @Test
    @DisplayName("Any indexed item clears the total-failure flag, even alongside failures")
    void indexedItemClearsTotalFailure() {
        DumWCCrawlReport report = new DumWCCrawlReport();
        report.recordFailure("https://x/dead", 404, "Not Found");
        report.recordIndexed();
        assertFalse(report.isTotalFailure());
        assertEquals(1, report.indexedCount());
        assertEquals(1, report.failureCount());
    }

    @Test
    @DisplayName("A run with only failures stays a total failure")
    void onlyFailuresIsTotalFailure() {
        DumWCCrawlReport report = new DumWCCrawlReport();
        report.recordFailure("https://x/a", 403, "Forbidden");
        report.recordFailure("https://x/b", 0, "Connection timed out");
        assertTrue(report.isTotalFailure());
        assertEquals(2, report.failureCount());
    }

    @Test
    @DisplayName("Failures are classified: 403/429 block, 0/404 dead, the rest other")
    void classifiesFailures() {
        assertTrue(new DumWCCrawlReport.Failure("u", 403, "").blocked());
        assertTrue(new DumWCCrawlReport.Failure("u", 429, "").blocked());
        assertTrue(new DumWCCrawlReport.Failure("u", 0, "").dead());
        assertTrue(new DumWCCrawlReport.Failure("u", 404, "").dead());
        DumWCCrawlReport.Failure serverError = new DumWCCrawlReport.Failure("u", 500, "");
        assertFalse(serverError.blocked());
        assertFalse(serverError.dead());
    }

    @Test
    @DisplayName("The summary reports the indexed count and a block/dead/other breakdown")
    void summaryBreakdown() {
        DumWCCrawlReport report = new DumWCCrawlReport();
        report.recordIndexed();
        report.recordFailure("https://x/a", 403, "Forbidden");
        report.recordFailure("https://x/b", 404, "Not Found");
        report.recordFailure("https://x/c", 500, "Server Error");

        String summary = report.summary();

        assertTrue(summary.contains("indexed=1"), summary);
        assertTrue(summary.contains("failures=3"), summary);
        assertTrue(summary.contains("blocked=1"), summary);
        assertTrue(summary.contains("dead=1"), summary);
        assertTrue(summary.contains("other=1"), summary);
    }
}
