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

package com.viglet.dumont.connector.plugin.catalog.persistence.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The calls one JSON API catalog source spent in one budget window (roadmap <strong>D92</strong>),
 * so a day, week or month allowance holds across runs and restarts.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "catalog_json_api_call",
        indexes = @Index(name = "idx_catalog_json_api_call", columnList = "source_name,budget_window,window_start"))
public class DumCatalogJsonApiCall implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    /** {@code DAY}, {@code WEEK} or {@code MONTH}. */
    @Column(name = "budget_window", nullable = false, length = 8)
    private String window;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "calls", nullable = false)
    private long calls;
}
