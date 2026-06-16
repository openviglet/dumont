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

/**
 * How many of a run's published documents carried a value for one declared field (roadmap
 * <strong>D15</strong>).
 *
 * <p>
 * It is the same metric the D14 dry-run preview computes over a sample, but measured over the
 * <em>full</em> index run and persisted per run so it can be compared across runs. A field whose
 * {@code percent} drops sharply between two runs is almost always a layout/selector break — see
 * {@code DumCoverageMonitor}.
 *
 * @param field     the declared field name
 * @param type      the search-engine field type name
 * @param mandatory whether the field is declared mandatory
 * @param present   how many published documents carried a value for the field
 * @param total     the number of published documents in the run
 * @param percent   {@code present / total} as a 0–100 percentage (0 when {@code total} is 0)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumFieldCoverage(String field, String type, boolean mandatory, int present, int total,
        int percent) {
}
