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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.manifest.TurSNManifestCustomFacet;

/**
 * The declared field set of a structured source — its <em>schema-as-code</em>.
 *
 * <p>
 * A schema is an ordered, immutable manifest of {@link DumStructuredField}s. It is declared once by
 * the source and drives two things: the {@link TurSNJobAttributeSpec}s published with every
 * document, and the grounding contract enforced by {@link DumStructuredRecord} — a value can only
 * be set on a field the schema declares, and an absent field is omitted, never sent as {@code ""}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumStructuredSchema {

    private final Map<String, DumStructuredField> fields;
    private final List<TurSNManifestCustomFacet> customFacets;

    private DumStructuredSchema(Map<String, DumStructuredField> fields,
            List<TurSNManifestCustomFacet> customFacets) {
        this.fields = Collections.unmodifiableMap(fields);
        this.customFacets = List.copyOf(customFacets);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** All declared fields, in declaration order. */
    public List<DumStructuredField> fields() {
        return new ArrayList<>(fields.values());
    }

    public Optional<DumStructuredField> field(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    public boolean hasField(String name) {
        return fields.containsKey(name);
    }

    /** Names of fields declared {@code mandatory}. */
    public List<String> mandatoryFieldNames() {
        return fields.values().stream()
                .filter(DumStructuredField::mandatory)
                .map(DumStructuredField::name)
                .toList();
    }

    /** The Turing attribute specs published alongside every document of this source. */
    public List<TurSNJobAttributeSpec> toAttributeSpecs() {
        return fields.values().stream()
                .map(DumStructuredField::toAttributeSpec)
                .toList();
    }

    /**
     * The custom facets provisioned with the site (D144): named buckets over a declared field, such
     * as "posted in the last 7 days". Empty unless the source declares one.
     */
    public List<TurSNManifestCustomFacet> customFacets() {
        return customFacets;
    }

    public static final class Builder {
        private final Map<String, DumStructuredField> fields = new LinkedHashMap<>();
        private final Map<String, TurSNManifestCustomFacet> customFacets = new LinkedHashMap<>();

        public Builder field(DumStructuredField field) {
            if (fields.putIfAbsent(field.name(), field) != null) {
                throw new IllegalArgumentException("Duplicate field declared: " + field.name());
            }
            return this;
        }

        public Builder string(String name) {
            return field(DumStructuredField.string(name));
        }

        public Builder text(String name) {
            return field(DumStructuredField.text(name));
        }

        public Builder facet(String name) {
            return field(DumStructuredField.facet(name));
        }

        /** A {@code TEXT} field projected onto Turing's {@code title} for the embedding header. */
        public Builder title(String name) {
            return field(DumStructuredField.title(name));
        }

        /** A {@code TEXT} field projected onto Turing's {@code text} for the embedded body. */
        public Builder body(String name) {
            return field(DumStructuredField.body(name));
        }

        /** A custom facet over a field this schema declares, provisioned with the site (D144). */
        public Builder customFacet(TurSNManifestCustomFacet customFacet) {
            if (customFacets.putIfAbsent(customFacet.name(), customFacet) != null) {
                throw new IllegalArgumentException("Duplicate custom facet declared: " + customFacet.name());
            }
            return this;
        }

        public DumStructuredSchema build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException("A structured schema must declare at least one field");
            }
            customFacets.values().stream()
                    .filter(facet -> !fields.containsKey(facet.field()))
                    .findFirst()
                    .ifPresent(facet -> {
                        throw new IllegalStateException("Custom facet '" + facet.name()
                                + "' is over undeclared field '" + facet.field() + "'");
                    });
            return new DumStructuredSchema(fields, new ArrayList<>(customFacets.values()));
        }
    }
}
