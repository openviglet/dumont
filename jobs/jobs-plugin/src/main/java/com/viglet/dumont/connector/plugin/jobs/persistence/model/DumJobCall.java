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

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One partner API call a source made (trabalharei <strong>TRA15</strong>).
 *
 * <p>
 * {@code DumJobCallBudget} only counts what it is told about, so a daily, weekly or monthly cap
 * would reset on every run. {@code DumJobsStructuredSource} writes a row for each call the budget
 * grants and seeds the next run's budget with the rows still inside the longest window; rows older
 * than {@link #RETENTION_DAYS} days are pruned at the start of each run. Every adapter goes through
 * the same budget, so this table is shared by all of them.
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
@Table(name = "job_call",
        indexes = @Index(name = "idx_job_call_source_called", columnList = "source_id, called_at"))
public class DumJobCall implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** How long a call is kept: one day past the longest (30-day) budget window. */
    public static final int RETENTION_DAYS = 31;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    /** The owning {@link DumJobSource#getId()}. */
    @Column(name = "source_id", nullable = false)
    private String sourceId;

    /** When the budget granted the call. */
    @Column(name = "called_at", nullable = false)
    private Instant calledAt;
}
