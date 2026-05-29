/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.dumont.connector.plugin.eds.persistence.model;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.dumont.spring.jpa.DumUuid;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One named facet derived from the page path. The {@code ordinal} field defines
 * the position in the source's {@code facetPathPattern} (e.g. for pattern
 * {@code /{locale}/{audience}/{section}/**}, the facets are ordered locale → 0,
 * audience → 1, section → 2).
 *
 * <p>{@code allowedValues} is an optional comma-separated whitelist for that
 * facet. When set, the matcher only consumes a path segment for this facet if
 * the segment is in the list — otherwise the facet stays empty and the next
 * facet considers the same segment. This is how {@code /pt-br/sala-de-imprensa}
 * resolves to {@code locale=pt-br, audience=∅, section=sala-de-imprensa} when
 * {@code audience} has {@code allowedValues="pf,pj"}.
 */
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "eds_facet")
@JsonIgnoreProperties({ "dumEDSSource" })
public class DumEDSFacet implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @DumUuid
    @Column(name = "id", nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "ordinal_position", nullable = false)
    private int ordinal;

    @Column(name = "allowed_values", length = 1024)
    private String allowedValues;

    @ManyToOne
    @JoinColumn(name = "eds_source_id", nullable = false)
    private DumEDSSource dumEDSSource;

    public DumEDSFacet(String name, int ordinal, String allowedValues, DumEDSSource source) {
        this.name = name;
        this.ordinal = ordinal;
        this.allowedValues = allowedValues;
        this.dumEDSSource = source;
    }
}
