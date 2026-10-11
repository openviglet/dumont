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

package com.viglet.dumont.connector.plugin.jobs.persistence.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One job title the fallback classifier answered (trabalharei <strong>TRA22</strong>), so each distinct
 * title is paid for once.
 *
 * <p>
 * The key is the SHA-256 of the title as {@code DumJobOccupationDictionary.normalize} reads it, so
 * "Analista de Dados Sênior" and "analista de dados" share one row. A {@code null} {@link #code} is a
 * cached "no family": the classifier answered and named none, which is not asked again. Both kinds are
 * tied to the CBO taxonomy version they were answered against; a row from another version is a miss.
 * A call that failed or was refused by the daily cap writes no row.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "job_occupation_cache")
public class DumJobOccupationCacheEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SHA-256 (hex) of the normalized title. */
    @Id
    @Column(name = "title_hash", nullable = false, length = 64)
    private String titleHash;

    /** The normalized title, for inspection only. */
    @Column(name = "normalized_title", nullable = false, length = 512)
    private String normalizedTitle;

    /** The CBO family code the classifier named, or {@code null} for "no family". */
    @Column(name = "code", length = 4)
    private String code;

    /** The version of the CBO taxonomy the answer was checked against. */
    @Column(name = "taxonomy_version", nullable = false)
    private int taxonomyVersion;

    /** When the classifier answered. */
    @Column(name = "classified_at", nullable = false)
    private Instant classifiedAt;
}
