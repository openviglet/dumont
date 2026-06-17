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

package com.viglet.dumont.connector.commons.spi.structured.provenance;

/**
 * The provenance of one declared field within a single index run (roadmap <strong>D16</strong>) —
 * the representative origin the run observed for the field, plus how many of the run's documents
 * carried it.
 *
 * <p>
 * A run uses one extraction rule per field, so {@code strategy}/{@code selector} are stable across
 * the documents and a single representative is faithful. The {@code present}/{@code total} counts let
 * {@code DumProvenanceMonitor} answer "when was this field last seen": a field with {@code present}
 * {@code = 0} stopped being declared by the source in this run.
 *
 * @param field      the declared field name
 * @param strategy   the strategy that produced the field's value (or {@code null} if never present)
 * @param selector   the selector/path the value was read from (or {@code null})
 * @param confidence an optional 0–1 confidence (or {@code null})
 * @param present    how many of the run's documents carried a value for the field
 * @param total      the number of documents the run published
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumFieldProvenanceEntry(String field, String strategy, String selector,
        Double confidence, int present, int total) {

    /** Whether any document in the run carried this field. */
    public boolean isPresent() {
        return present > 0;
    }
}
