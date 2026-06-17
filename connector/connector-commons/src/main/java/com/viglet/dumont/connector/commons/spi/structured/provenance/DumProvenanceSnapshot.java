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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;

/**
 * The per-field provenance of one full index run of a structured source (roadmap
 * <strong>D16</strong>) — the unit of the provenance history a {@code DumProvenanceHistoryStore}
 * keeps per source.
 *
 * <p>
 * Captured by {@code DumStructuredSourcePlugin} at the end of each full {@code indexAll}, it records,
 * per declared field, the origin the run observed and how many documents carried it — never the field
 * values themselves, so a long history stays small. Read back across runs by
 * {@code DumProvenanceMonitor}, it answers "when was each field last seen" so a field a source
 * stopped declaring shows up as last-seen at an earlier run.
 *
 * @param source        the source the run indexed
 * @param capturedAt    when the run finished (epoch millis; stamped by the history store)
 * @param documentCount the number of documents the run published
 * @param fields        per declared field, the run's representative origin and present count
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumProvenanceSnapshot(String source, long capturedAt, int documentCount,
        List<DumFieldProvenanceEntry> fields) {

    public DumProvenanceSnapshot {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /** The provenance of one declared field in this run, if the schema declared it. */
    public Optional<DumFieldProvenanceEntry> field(String name) {
        return fields.stream().filter(entry -> entry.field().equals(name)).findFirst();
    }

    public static Builder builder(DumStructuredSchema schema) {
        return new Builder(schema);
    }

    /**
     * Accumulates the per-field origin and present count as a full index run publishes documents.
     * Mirrors the D14 preview-snapshot / D15 coverage capture: a by-product of the run, not a second
     * extraction pass. The representative origin for a field is the first non-null provenance seen.
     */
    public static final class Builder {
        private final DumStructuredSchema schema;
        private final Map<String, DumFieldProvenance> origins = new LinkedHashMap<>();
        private final Map<String, Integer> present = new LinkedHashMap<>();
        private int documentCount;

        private Builder(DumStructuredSchema schema) {
            this.schema = schema;
        }

        /** Record one published document by its grounded values and per-field provenance. */
        public Builder add(Map<String, Object> values, Map<String, DumFieldProvenance> provenance) {
            documentCount++;
            if (values == null) {
                return this;
            }
            values.keySet().forEach(field -> present.merge(field, 1, Integer::sum));
            if (provenance != null) {
                provenance.forEach(origins::putIfAbsent);
            }
            return this;
        }

        public int documentCount() {
            return documentCount;
        }

        /** Per declared field, in schema declaration order. */
        public List<DumFieldProvenanceEntry> fields() {
            return schema.fields().stream().map(field -> {
                DumFieldProvenance origin = origins.get(field.name());
                return new DumFieldProvenanceEntry(field.name(),
                        origin == null ? null : origin.strategy(),
                        origin == null ? null : origin.selector(),
                        origin == null ? null : origin.confidence(),
                        present.getOrDefault(field.name(), 0), documentCount);
            }).toList();
        }
    }
}
