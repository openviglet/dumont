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

package com.viglet.dumont.connector.persistence.model;

import java.io.Serial;
import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One declared field's provenance within a {@link DumProvenanceRunModel} (roadmap
 * <strong>D16</strong>) — the origin the run observed for the field and how many of the run's
 * documents carried it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Embeddable
public class DumProvenanceFieldModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "field_name")
    private String fieldName;
    @Column
    private String strategy;
    @Column(length = 1024)
    private String selector;
    @Column
    private Double confidence;
    @Column
    private int present;
    @Column(name = "total_docs")
    private int total;
}
