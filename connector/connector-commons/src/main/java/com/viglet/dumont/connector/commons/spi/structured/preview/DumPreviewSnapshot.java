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

package com.viglet.dumont.connector.commons.spi.structured.preview;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * A lightweight, comparable fingerprint of a structured source's published documents — the baseline
 * a {@link DumStructuredPreviewer dry-run preview} (roadmap <strong>D14</strong>) diffs against.
 *
 * <p>
 * A snapshot is captured by {@code DumStructuredSourcePlugin} at the end of a full {@code indexAll}
 * run and stored per source through a {@link DumPreviewSnapshotStore}. It carries only what a diff
 * needs — a schema fingerprint and, per document id, a content {@link DumPreviewDoc#digest() digest}
 * and the set of fields that were present — never the field values themselves, so it stays small
 * even for large catalogs. A later preview compares the would-be sample against this baseline to
 * report add / change / remove counts and newly-empty fields, all without touching the index.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public record DumPreviewSnapshot(List<DumPreviewFieldSpec> schema,
        Map<String, DumPreviewDoc> documents) {

    public DumPreviewSnapshot {
        schema = schema == null ? List.of() : List.copyOf(schema);
        documents = documents == null ? Map.of() : Map.copyOf(documents);
    }

    /** The neutral baseline used on first onboarding — everything sampled later reads as "added". */
    public static DumPreviewSnapshot empty() {
        return new DumPreviewSnapshot(List.of(), Map.of());
    }

    public boolean isEmpty() {
        return schema.isEmpty() && documents.isEmpty();
    }

    public static Builder builder(DumStructuredSchema schema) {
        return new Builder(schema);
    }

    /** Accumulates document fingerprints as a full index run publishes them. */
    public static final class Builder {
        private final List<DumPreviewFieldSpec> schema;
        private final Map<String, DumPreviewDoc> documents = new LinkedHashMap<>();

        private Builder(DumStructuredSchema schema) {
            this.schema = DumPreviewFieldSpec.of(schema);
        }

        /** Record one published document by its grounded attributes (the synthetic id is ignored). */
        public Builder add(String id, Map<String, Object> attributes) {
            documents.put(id, DumPreviewDoc.of(attributes));
            return this;
        }

        public DumPreviewSnapshot build() {
            return new DumPreviewSnapshot(schema, documents);
        }
    }

    /** A serializable copy of one declared field, so a schema diff survives without the live schema. */
    public record DumPreviewFieldSpec(String name, String type, boolean multiValued, boolean facet,
            boolean mandatory) {

        public static List<DumPreviewFieldSpec> of(DumStructuredSchema schema) {
            return schema == null ? List.of()
                    : schema.fields().stream().map(DumPreviewFieldSpec::of).toList();
        }

        public static DumPreviewFieldSpec of(DumStructuredField field) {
            return new DumPreviewFieldSpec(field.name(), field.type().name(), field.multiValued(),
                    field.facet(), field.mandatory());
        }
    }

    /**
     * The fingerprint of a single document: a content {@code digest} (changes when any grounded
     * value changes) plus the sorted set of fields that carried a value (so a later run can name the
     * fields that went newly-empty).
     */
    public record DumPreviewDoc(String digest, List<String> fields) {

        public DumPreviewDoc {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }

        /** Build a fingerprint from a would-be (or just-published) document's grounded attributes. */
        public static DumPreviewDoc of(Map<String, Object> attributes) {
            List<String> presentFields = attributes.keySet().stream()
                    .filter(key -> !TurSNFieldName.ID.equals(key))
                    .sorted()
                    .toList();
            return new DumPreviewDoc(digest(attributes), presentFields);
        }

        /** A stable, content-derived digest; equal digests mean an unchanged document. */
        static String digest(Map<String, Object> attributes) {
            String canonical = attributes.entrySet().stream()
                    .filter(entry -> !TurSNFieldName.ID.equals(entry.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + valueString(entry.getValue()))
                    .collect(Collectors.joining("\n"));
            return Integer.toHexString(canonical.hashCode());
        }

        private static String valueString(Object value) {
            if (value instanceof Collection<?> collection) {
                return collection.stream().map(String::valueOf).collect(Collectors.joining("|"));
            }
            return String.valueOf(value);
        }
    }
}
