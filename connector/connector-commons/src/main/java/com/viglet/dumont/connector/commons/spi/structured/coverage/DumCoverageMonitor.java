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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.coverage.DumCoverageDrift.FieldDrift;
import com.viglet.dumont.connector.commons.spi.structured.preview.DumPreviewSnapshot;

/**
 * The field-coverage &amp; drift engine (roadmap <strong>D15</strong>): pure functions over the data
 * the structured pipeline already produces.
 *
 * <p>
 * {@link #coverage(DumStructuredSchema, DumPreviewSnapshot)} derives per-field coverage from the same
 * document fingerprint a full {@code indexAll} captures for the D14 baseline — no extra pass over the
 * source. {@link #drift(DumCoverageSnapshot, DumCoverageSnapshot, int)} compares two runs and flags a
 * field whose coverage dropped by at least a threshold, the signal of a layout/selector break.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumCoverageMonitor {

    /** Default percentage-point drop that counts as drift when a caller does not configure one. */
    public static final int DEFAULT_DROP_THRESHOLD = 25;

    private DumCoverageMonitor() {
    }

    /**
     * Per-field coverage over the documents captured in {@code snapshot}, in schema declaration
     * order. Reuses the per-document present-field sets the snapshot already holds, so coverage is a
     * by-product of a full index run rather than a second extraction pass.
     */
    public static List<DumFieldCoverage> coverage(DumStructuredSchema schema,
            DumPreviewSnapshot snapshot) {
        int total = snapshot.documents().size();
        return schema.fields().stream().map(field -> {
            int present = (int) snapshot.documents().values().stream()
                    .filter(doc -> doc.fields().contains(field.name()))
                    .count();
            int percent = total == 0 ? 0 : Math.round(present * 100f / total);
            return new DumFieldCoverage(field.name(), field.type().name(), field.mandatory(), present,
                    total, percent);
        }).toList();
    }

    /**
     * Compare the older {@code previous} run against the newer {@code current} run and flag every
     * field whose coverage dropped by at least {@code dropThreshold} percentage points. A field
     * present in only one run is compared against an implied 0%. Returns a {@link DumCoverageDrift#none
     * neutral} result when either snapshot is missing (a source needs two runs before drift exists).
     */
    public static DumCoverageDrift drift(DumCoverageSnapshot previous, DumCoverageSnapshot current,
            int dropThreshold) {
        int threshold = dropThreshold <= 0 ? DEFAULT_DROP_THRESHOLD : dropThreshold;
        if (previous == null || current == null) {
            String source;
            if (current != null) {
                source = current.source();
            } else if (previous != null) {
                source = previous.source();
            } else {
                source = null;
            }
            return DumCoverageDrift.none(source, threshold);
        }
        Set<String> names = new LinkedHashSet<>();
        current.fields().forEach(field -> names.add(field.field()));
        previous.fields().forEach(field -> names.add(field.field()));

        List<FieldDrift> fields = new ArrayList<>();
        boolean hasDrift = false;
        for (String name : names) {
            int prev = previous.field(name).map(DumFieldCoverage::percent).orElse(0);
            int curr = current.field(name).map(DumFieldCoverage::percent).orElse(0);
            int delta = curr - prev;
            boolean drift = delta <= -threshold;
            hasDrift = hasDrift || drift;
            fields.add(new FieldDrift(name, prev, curr, delta, drift));
        }
        return new DumCoverageDrift(current.source(), hasDrift, threshold, previous.capturedAt(),
                current.capturedAt(), fields);
    }

    /**
     * The run warnings a drift raises (roadmap <strong>D130</strong>): one per field whose coverage
     * dropped by at least {@code dropThreshold} points between {@code previous}, the last complete run,
     * and this run's {@code coverage}. None without a previous run, or with a threshold {@code <= 0},
     * which is how a source turns the check off.
     */
    public static List<String> driftWarnings(DumCoverageSnapshot previous, String source,
            int documentCount, List<DumFieldCoverage> coverage, int dropThreshold) {
        if (previous == null || dropThreshold <= 0) {
            return List.of();
        }
        DumCoverageSnapshot current = new DumCoverageSnapshot(source, System.currentTimeMillis(),
                documentCount, coverage);
        return drift(previous, current, dropThreshold).fields().stream()
                .filter(FieldDrift::drift)
                .map(field -> "coverage of field '%s' dropped from %d%% to %d%%".formatted(field.field(),
                        field.previousPercent(), field.currentPercent()))
                .toList();
    }
}
