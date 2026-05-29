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
 * Optional rename map for a single document attribute, e.g. EDS-sheet
 * {@code title} → SNSite attribute {@code page_title}. Sources without
 * explicit mappings get identity mapping (source field name === target).
 */
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "eds_field_mapping")
@JsonIgnoreProperties({ "dumEDSSource" })
public class DumEDSFieldMapping implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @DumUuid
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "source_field", nullable = false)
    private String sourceField;

    @Column(name = "target_field", nullable = false)
    private String targetField;

    @ManyToOne
    @JoinColumn(name = "eds_source_id", nullable = false)
    private DumEDSSource dumEDSSource;

    public DumEDSFieldMapping(String sourceField, String targetField, DumEDSSource source) {
        this.sourceField = sourceField;
        this.targetField = targetField;
        this.dumEDSSource = source;
    }
}
