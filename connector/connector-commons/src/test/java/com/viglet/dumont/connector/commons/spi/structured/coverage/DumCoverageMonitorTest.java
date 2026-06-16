/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.commons.spi.structured.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageDrift.FieldDrift;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshot;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** Unit tests for the D15 coverage + drift engine — both pure functions over already-captured data. */
class DumCoverageMonitorTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true).build())
            .text("workload")
            .build();

    @Test
    void coverageCountsPresentFieldsPerSchemaField() {
        DumPreviewSnapshot snapshot = DumPreviewSnapshot.builder(SCHEMA)
                .add("c1", java.util.Map.of("title", "Algorithms", "workload", "60h"))
                .add("c2", java.util.Map.of("title", "Calculus")) // workload absent
                .build();

        List<DumFieldCoverage> coverage = DumCoverageMonitor.coverage(SCHEMA, snapshot);

        assertEquals(2, coverage.size());
        DumFieldCoverage title = coverage.get(0);
        assertEquals("title", title.field());
        assertEquals(100, title.percent());
        DumFieldCoverage workload = coverage.get(1);
        assertEquals("workload", workload.field());
        assertEquals(1, workload.present());
        assertEquals(2, workload.total());
        assertEquals(50, workload.percent());
    }

    @Test
    void driftFlagsAFieldWhoseCoverageDroppedSharply() {
        DumCoverageSnapshot previous = new DumCoverageSnapshot("courses", 1_000L, 2, List.of(
                new DumFieldCoverage("title", "TEXT", true, 2, 2, 100),
                new DumFieldCoverage("workload", "TEXT", false, 2, 2, 100)));
        DumCoverageSnapshot current = new DumCoverageSnapshot("courses", 2_000L, 2, List.of(
                new DumFieldCoverage("title", "TEXT", true, 2, 2, 100),
                new DumFieldCoverage("workload", "TEXT", false, 0, 2, 0))); // selector broke

        DumCoverageDrift drift = DumCoverageMonitor.drift(previous, current, 25);

        assertTrue(drift.hasDrift());
        assertEquals(List.of("workload"), drift.driftedFields());
        FieldDrift workload = drift.fields().stream()
                .filter(field -> field.field().equals("workload")).findFirst().orElseThrow();
        assertEquals(-100, workload.delta());
        assertEquals(2_000L, drift.currentRunAt());
    }

    @Test
    void noDriftWhenCoverageHoldsOrImproves() {
        DumCoverageSnapshot previous = new DumCoverageSnapshot("courses", 1_000L, 2, List.of(
                new DumFieldCoverage("workload", "TEXT", false, 1, 2, 50)));
        DumCoverageSnapshot current = new DumCoverageSnapshot("courses", 2_000L, 2, List.of(
                new DumFieldCoverage("workload", "TEXT", false, 2, 2, 100)));

        DumCoverageDrift drift = DumCoverageMonitor.drift(previous, current, 25);

        assertFalse(drift.hasDrift());
        assertTrue(drift.driftedFields().isEmpty());
    }

    @Test
    void noDriftComputedWithASingleRun() {
        DumCoverageSnapshot only = new DumCoverageSnapshot("courses", 1_000L, 2, List.of());
        assertFalse(DumCoverageMonitor.drift(null, only, 25).hasDrift());
    }
}
