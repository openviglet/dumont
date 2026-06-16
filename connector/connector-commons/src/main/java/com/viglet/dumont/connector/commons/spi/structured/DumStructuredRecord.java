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

package com.viglet.dumont.connector.commons.spi.structured;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * A schema-bound, typed record produced by {@link DumStructuredSource#extract(String)}.
 *
 * <p>
 * A record holds a content {@code id} plus the values for the fields declared by its
 * {@link DumStructuredSchema}. It is the structured-source counterpart of the loose
 * {@code Map<String, Object>} the web crawler builds from CSS selectors — except that here every
 * value is bound to a declared field and validated against its {@link TurSEFieldType}.
 *
 * <p>
 * <strong>Grounding contract.</strong> A value is stored only when the source actually has one. The
 * builder silently drops {@code null} values, blank strings, and empty/blank collection entries, so
 * an absent field never reaches the index as {@code ""} — it is simply omitted. The record never
 * invents a value to satisfy the schema; mandatory fields left unset are reported by
 * {@link #missingMandatoryFields()} and the indexing pipeline skips the record rather than
 * fabricating data.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumStructuredRecord {

    private final String id;
    private final DumStructuredSchema schema;
    private final Map<String, Object> values;

    private DumStructuredRecord(String id, DumStructuredSchema schema, Map<String, Object> values) {
        this.id = id;
        this.schema = schema;
        this.values = Collections.unmodifiableMap(values);
    }

    public static Builder builder(String id, DumStructuredSchema schema) {
        return new Builder(id, schema);
    }

    public String id() {
        return id;
    }

    public DumStructuredSchema schema() {
        return schema;
    }

    /** The grounded field values — only fields the source actually supplied are present. */
    public Map<String, Object> values() {
        return values;
    }

    public boolean has(String field) {
        return values.containsKey(field);
    }

    /** Mandatory fields the source declared but left unset; non-empty means the record is invalid. */
    public List<String> missingMandatoryFields() {
        return schema.mandatoryFieldNames().stream()
                .filter(name -> !values.containsKey(name))
                .toList();
    }

    public static final class Builder {
        private final String id;
        private final DumStructuredSchema schema;
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder(String id, DumStructuredSchema schema) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Structured record id is required");
            }
            if (schema == null) {
                throw new IllegalArgumentException("Structured record schema is required");
            }
            this.id = id;
            this.schema = schema;
        }

        /**
         * Set a field value, enforcing the grounding contract. The field must be declared by the
         * schema; the value is validated against the field's type. {@code null} values, blank
         * strings, and empty collections are dropped (the field stays absent), so nothing is ever
         * published as an empty value.
         *
         * @throws IllegalArgumentException if the field is not declared, the value type is
         *         incompatible with the declared {@link TurSEFieldType}, or more than one value is
         *         given for a single-valued field
         */
        public Builder set(String fieldName, Object value) {
            DumStructuredField field = schema.field(fieldName).orElseThrow(
                    () -> new IllegalArgumentException("Field not declared in schema: " + fieldName));
            Object grounded = ground(field, value);
            if (grounded != null) {
                values.put(fieldName, grounded);
            }
            return this;
        }

        private Object ground(DumStructuredField field, Object value) {
            if (value instanceof Collection<?> collection) {
                return groundCollection(field, collection);
            }
            if (isBlank(value)) {
                return null;
            }
            if (field.multiValued()) {
                List<Object> single = new ArrayList<>();
                single.add(validateType(field, value));
                return single;
            }
            return validateType(field, value);
        }

        private Object groundCollection(DumStructuredField field, Collection<?> collection) {
            if (!field.multiValued()) {
                List<?> kept = collection.stream().filter(v -> !isBlank(v)).toList();
                if (kept.isEmpty()) {
                    return null;
                }
                if (kept.size() > 1) {
                    throw new IllegalArgumentException(
                            "Field '" + field.name() + "' is single-valued but got " + kept.size()
                                    + " values");
                }
                return validateType(field, kept.getFirst());
            }
            List<Object> values = new ArrayList<>();
            collection.stream().filter(v -> !isBlank(v))
                    .forEach(v -> values.add(validateType(field, v)));
            return values.isEmpty() ? null : values;
        }

        private Object validateType(DumStructuredField field, Object value) {
            switch (field.type()) {
                case INT, LONG -> requireParsable(field, value, Long::parseLong);
                case FLOAT, DOUBLE, CURRENCY -> requireParsable(field, value, Double::parseDouble);
                case BOOL -> requireBoolean(field, value);
                default -> { /* STRING, TEXT, ARRAY, DATE accept the value as-is */ }
            }
            return value;
        }

        private void requireParsable(DumStructuredField field, Object value,
                java.util.function.Function<String, ?> parser) {
            if (value instanceof Number) {
                return;
            }
            try {
                parser.apply(value.toString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Field '" + field.name() + "' expects a "
                        + field.type() + " value but got: " + value, e);
            }
        }

        private void requireBoolean(DumStructuredField field, Object value) {
            if (value instanceof Boolean) {
                return;
            }
            String text = value.toString().trim();
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException(
                        "Field '" + field.name() + "' expects a BOOL value but got: " + value);
            }
        }

        private static boolean isBlank(Object value) {
            return value == null || (value instanceof CharSequence cs && cs.toString().isBlank());
        }

        public DumStructuredRecord build() {
            return new DumStructuredRecord(id, schema, values);
        }
    }
}
