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

import java.util.List;

/**
 * The outcome of comparing the two most recent coverage runs of a source (roadmap
 * <strong>D15</strong>): per field, how its coverage moved, and a {@link #hasDrift() drift flag}
 * raised when any field's coverage dropped by at least {@code dropThreshold} percentage points.
 *
 * <p>
 * A sharp coverage drop almost always means a layout/selector break upstream — a field that used to
 * be extracted no longer is — which would otherwise stay invisible until search quality degrades.
 * Surfacing it here turns a silent regression into an actionable flag naming the affected fields.
 *
 * @param source        the source compared
 * @param hasDrift      whether at least one field dropped by {@code dropThreshold} points or more
 * @param dropThreshold the percentage-point drop that counts as drift
 * @param previousRunAt when the older run was captured (epoch millis)
 * @param currentRunAt  when the newer run was captured (epoch millis)
 * @param fields        per field, its previous/current coverage and whether it drifted
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumCoverageDrift(String source, boolean hasDrift, int dropThreshold,
        long previousRunAt, long currentRunAt, List<FieldDrift> fields) {

    public DumCoverageDrift {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /** A neutral result — no drift could be computed (fewer than two runs, or a missing snapshot). */
    public static DumCoverageDrift none(String source, int dropThreshold) {
        return new DumCoverageDrift(source, false, dropThreshold, 0L, 0L, List.of());
    }

    /** Names of the fields whose coverage dropped sharply — what an operator needs to investigate. */
    public List<String> driftedFields() {
        return fields.stream().filter(FieldDrift::drift).map(FieldDrift::field).toList();
    }

    /**
     * One field's coverage movement between the two runs. {@code delta = current - previous}; a
     * negative {@code delta} at or beyond the threshold sets {@code drift}.
     */
    public record FieldDrift(String field, int previousPercent, int currentPercent, int delta,
            boolean drift) {
    }
}
