/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.db.persistence.model;

import java.io.Serializable;

import com.viglet.dumont.connector.commons.spi.structured.DumEmbeddingRole;
import com.viglet.turing.commons.se.field.TurSEFieldType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A declared column&rarr;field mapping for a {@link DumDbSource} (roadmap <strong>D20</strong>).
 *
 * <p>
 * Each entry names a query column (the {@code AS} alias) and the search-engine shape it should take
 * once indexed — its {@link TurSEFieldType}, whether it is multi-valued, a facet, or mandatory.
 * Collectively the source's fields are the schema-as-code that the structured-source SPI publishes
 * with every document and grounds every record against. A source that declares no fields falls back
 * to a schema derived from the query's result-set metadata (parity with the legacy all-columns path).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Embeddable
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DumDbField implements Serializable {

    /** The field name in the indexed document — the query column label ({@code AS} alias). */
    @Column(name = "field_name")
    private String name;

    /** The search-engine field type; defaults to {@link TurSEFieldType#STRING} when unset. */
    @Enumerated(EnumType.STRING)
    @Column(name = "field_type")
    private TurSEFieldType type;

    @Column(name = "field_multi_valued")
    private boolean multiValued;

    @Column(name = "field_facet")
    private boolean facet;

    @Column(name = "field_mandatory")
    private boolean mandatory;

    @Column(name = "field_description")
    private String description;

    /** Index-time embedding projection (title/abstract/text); defaults to {@link DumEmbeddingRole#NONE}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "field_embedding_role")
    private DumEmbeddingRole embeddingRole;
}
