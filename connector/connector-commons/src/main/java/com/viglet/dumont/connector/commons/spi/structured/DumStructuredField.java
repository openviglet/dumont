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

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Declaration of a single field in a {@link DumStructuredSchema}.
 *
 * <p>
 * A structured source declares its fields <em>once</em> — name, search-engine type, whether the
 * field is multi-valued, a facet, and/or mandatory — instead of carrying loose CSS-selector
 * strings around. The declaration maps directly to a Turing {@link TurSNJobAttributeSpec}, so the
 * schema is published alongside the document and the indexing side knows the field shape up front.
 *
 * @param name          the field name as it appears in the indexed document (required)
 * @param type          the search-engine field type (defaults to {@link TurSEFieldType#STRING})
 * @param multiValued   whether the field may hold more than one value
 * @param facet         whether the field is exposed as a facet
 * @param mandatory     whether a record without this field is invalid (skipped, never fabricated)
 * @param description   an optional human-readable description
 * @param embeddingRole the field's role in Turing's index-time embedding (defaults to
 *                      {@link DumEmbeddingRole#NONE}); a non-{@code NONE} role projects the field's
 *                      value onto the canonical key Turing's RAG partitioner reads
 * @param searchWeight  how much a match in this field counts in search, 1 to 100 as in the
 *                      Turing console, or {@code null} for Turing's default of 1 (roadmap
 *                      <strong>D140</strong>, sent in the site manifest since Turing T1082)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumStructuredField(String name, TurSEFieldType type, boolean multiValued,
        boolean facet, boolean mandatory, String description, DumEmbeddingRole embeddingRole,
        Integer searchWeight) {

    /** The lowest and highest search weight Turing accepts. */
    public static final int MIN_SEARCH_WEIGHT = 1;
    public static final int MAX_SEARCH_WEIGHT = 100;

    public DumStructuredField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Structured field name is required");
        }
        if (type == null) {
            type = TurSEFieldType.STRING;
        }
        if (embeddingRole == null) {
            embeddingRole = DumEmbeddingRole.NONE;
        }
        if (searchWeight != null && (searchWeight < MIN_SEARCH_WEIGHT || searchWeight > MAX_SEARCH_WEIGHT)) {
            throw new IllegalArgumentException("Structured field '%s' search weight must be %d to %d, not %d"
                    .formatted(name, MIN_SEARCH_WEIGHT, MAX_SEARCH_WEIGHT, searchWeight));
        }
    }

    /** A field at Turing's default search weight, as declared before D140. */
    public DumStructuredField(String name, TurSEFieldType type, boolean multiValued, boolean facet,
            boolean mandatory, String description, DumEmbeddingRole embeddingRole) {
        this(name, type, multiValued, facet, mandatory, description, embeddingRole, null);
    }

    /** A single-valued, non-facet, optional {@link TurSEFieldType#STRING} field. */
    public static DumStructuredField string(String name) {
        return builder(name).type(TurSEFieldType.STRING).build();
    }

    /** A single-valued, non-facet, optional {@link TurSEFieldType#TEXT} field. */
    public static DumStructuredField text(String name) {
        return builder(name).type(TurSEFieldType.TEXT).build();
    }

    /** A multi-valued, faceted {@link TurSEFieldType#STRING} field. */
    public static DumStructuredField facet(String name) {
        return builder(name).type(TurSEFieldType.STRING).multiValued(true).facet(true).build();
    }

    /**
     * A {@link TurSEFieldType#TEXT} field that is the document's primary heading — projected onto
     * Turing's {@code title} so the embedding chunk header carries the document identity.
     */
    public static DumStructuredField title(String name) {
        return builder(name).type(TurSEFieldType.TEXT)
                .embeddingRole(DumEmbeddingRole.TITLE).build();
    }

    /**
     * A {@link TurSEFieldType#TEXT} field holding long-form content — projected onto Turing's
     * {@code text} so it reaches the embedded body even under a non-standard field name.
     */
    public static DumStructuredField body(String name) {
        return builder(name).type(TurSEFieldType.TEXT)
                .embeddingRole(DumEmbeddingRole.BODY).build();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Maps this declaration to the Turing SDK attribute spec published with every document. */
    public TurSNJobAttributeSpec toAttributeSpec() {
        return TurSNJobAttributeSpec.builder()
                .name(name)
                .type(type)
                .multiValued(multiValued)
                .facet(facet)
                .mandatory(mandatory)
                .description(description)
                .searchWeight(searchWeight == null ? null : searchWeight.floatValue())
                .build();
    }

    public static final class Builder {
        private final String name;
        private TurSEFieldType type = TurSEFieldType.STRING;
        private boolean multiValued;
        private boolean facet;
        private boolean mandatory;
        private String description;
        private DumEmbeddingRole embeddingRole = DumEmbeddingRole.NONE;
        private Integer searchWeight;

        private Builder(String name) {
            this.name = name;
        }

        public Builder type(TurSEFieldType type) {
            this.type = type;
            return this;
        }

        public Builder multiValued(boolean multiValued) {
            this.multiValued = multiValued;
            return this;
        }

        public Builder facet(boolean facet) {
            this.facet = facet;
            return this;
        }

        public Builder mandatory(boolean mandatory) {
            this.mandatory = mandatory;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder embeddingRole(DumEmbeddingRole embeddingRole) {
            this.embeddingRole = embeddingRole;
            return this;
        }

        /**
         * How much a match in this field counts in search (D140): 1 to 100 as in the Turing
         * console, where 1 is the default every undeclared field gets.
         */
        public Builder searchWeight(int searchWeight) {
            this.searchWeight = searchWeight;
            return this;
        }

        public DumStructuredField build() {
            return new DumStructuredField(name, type, multiValued, facet, mandatory, description,
                    embeddingRole, searchWeight);
        }
    }
}
