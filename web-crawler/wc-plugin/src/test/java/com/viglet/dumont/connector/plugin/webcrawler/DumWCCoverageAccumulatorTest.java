/*
 * Copyright (C) 2016-2026 the original author or authors.
 */
package com.viglet.dumont.connector.plugin.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.coverage.DumFieldCoverage;

/**
 * Coverage for roadmap D23 — the WC field-presence accumulator. The web crawler stays a generic
 * link-spider, but its declared attribute-mapping names form an implicit field set; this records a
 * per-page presence signal and turns it into the {@link DumFieldCoverage} the D15 history consumes.
 */
@DisplayName("DumWCCoverageAccumulator field-presence signal (D23)")
class DumWCCoverageAccumulatorTest {

    private static Map<String, DumFieldCoverage> byField(List<DumFieldCoverage> coverage) {
        return coverage.stream().collect(Collectors.toMap(DumFieldCoverage::field, c -> c));
    }

    @Test
    @DisplayName("No declared mappings — nothing to measure")
    void emptyWhenNoMappings() {
        DumWCCoverageAccumulator accumulator = new DumWCCoverageAccumulator(List.of());
        accumulator.recordPage(Set.of("title"));

        assertTrue(accumulator.isEmpty());
        assertTrue(accumulator.toFieldCoverage().isEmpty());
    }

    @Test
    @DisplayName("Per-field coverage is present-count over indexed pages")
    void coverageOverPages() {
        DumWCCoverageAccumulator accumulator = new DumWCCoverageAccumulator(List.of("title", "price"));
        accumulator.recordPage(Set.of("title", "price"));
        accumulator.recordPage(Set.of("title"));

        assertFalse(accumulator.isEmpty());
        assertEquals(2, accumulator.documentCount());

        Map<String, DumFieldCoverage> coverage = byField(accumulator.toFieldCoverage());
        assertEquals(2, coverage.get("title").present());
        assertEquals(100, coverage.get("title").percent());
        assertEquals(1, coverage.get("price").present());
        assertEquals(50, coverage.get("price").percent());
        coverage.values().forEach(c -> assertEquals(2, c.total()));
    }

    @Test
    @DisplayName("A mapping that matches zero pages reports 0% (the broken-mapping signal)")
    void brokenMappingReportsZero() {
        DumWCCoverageAccumulator accumulator = new DumWCCoverageAccumulator(List.of("title", "price"));
        accumulator.recordPage(Set.of("title"));
        accumulator.recordPage(Set.of("title"));

        Map<String, DumFieldCoverage> coverage = byField(accumulator.toFieldCoverage());
        assertEquals(100, coverage.get("title").percent());
        assertEquals(0, coverage.get("price").present());
        assertEquals(0, coverage.get("price").percent());
    }

    @Test
    @DisplayName("Keys not in the declared set are ignored")
    void ignoresUndeclaredKeys() {
        DumWCCoverageAccumulator accumulator = new DumWCCoverageAccumulator(List.of("title"));
        accumulator.recordPage(Set.of("title", "stray"));

        List<DumFieldCoverage> coverage = accumulator.toFieldCoverage();
        assertEquals(1, coverage.size());
        assertEquals("title", coverage.getFirst().field());
        assertEquals(1, coverage.getFirst().present());
    }

    @Test
    @DisplayName("Declaration order and blank/duplicate names are normalized")
    void normalizesDeclaredNames() {
        DumWCCoverageAccumulator accumulator = new DumWCCoverageAccumulator(
                java.util.Arrays.asList("title", " ", "price", "title", null));

        List<String> fields = accumulator.toFieldCoverage().stream().map(DumFieldCoverage::field)
                .toList();
        assertEquals(List.of("title", "price"), fields);
    }
}
