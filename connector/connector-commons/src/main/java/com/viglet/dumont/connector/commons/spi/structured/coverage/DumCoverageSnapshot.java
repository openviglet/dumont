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
import java.util.Optional;

/**
 * The per-field coverage of one full index run of a structured source (roadmap <strong>D15</strong>)
 * — the unit of the coverage history that {@code DumCoverageHistoryStore} keeps per source.
 *
 * <p>
 * Captured by {@code DumStructuredSourcePlugin} at the end of each full {@code indexAll}, it carries
 * only the aggregate per-field {@link DumFieldCoverage coverage} (never field values), so a long
 * history stays small. Two consecutive snapshots are all {@code DumCoverageMonitor} needs to flag
 * drift — a field whose coverage dropped sharply.
 *
 * @param source        the source the run indexed
 * @param capturedAt    when the run finished (epoch millis; stamped by the history store)
 * @param documentCount the number of documents the run published
 * @param fields        per declared field, how many of those documents carried a value
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumCoverageSnapshot(String source, long capturedAt, int documentCount,
        List<DumFieldCoverage> fields) {

    public DumCoverageSnapshot {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /** The coverage of one declared field in this run, if the schema declared it. */
    public Optional<DumFieldCoverage> field(String name) {
        return fields.stream().filter(coverage -> coverage.field().equals(name)).findFirst();
    }
}
