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
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One posting's place in the cross-source fingerprint index (trabalharei <strong>TRA23</strong>).
 *
 * <p>
 * Sources run one at a time, and Dumont's cross-source resolution (D18) only sees the records of a
 * single {@code crawl()} call, in memory. This table is what lets a later source's run find the
 * postings an earlier source's run fetched: one row per posting of any source, keyed by
 * {@code (source_id, job_id)} and indexed by {@link #fingerprint}. Every row of a fingerprint is one
 * member of the group published as a single {@code job-<fingerprint>} document.
 *
 * <p>
 * A row outlives its posting's {@link #expiresAt}: while any other member of its group is still open,
 * the expired row is what tells its source to keep publishing the group document, so that source's
 * run-end sweep does not deindex it (see {@code DumJobDeduplicator}). The rows of a group are deleted
 * together once no member is open.
 *
 * <p>
 * {@link #applyUrl} keeps every member's apply link — the published schema has a single
 * {@code applyUrl} and the trabalharei {@code JOB_FIELDS} contract has no field for the others, so
 * they stay here for a later task.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "job_fingerprint",
        uniqueConstraints = @UniqueConstraint(columnNames = { "source_id", "job_id" }),
        indexes = @Index(name = "idx_job_fingerprint_fp", columnList = "fingerprint"))
public class DumJobFingerprintEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    /** The group key hash; the group document is published as {@code job-<fingerprint>}. */
    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    /** The owning {@link DumJobSource#getId()}. */
    @Column(name = "source_id", nullable = false)
    private String sourceId;

    /** The owning source's name, for the duplicate-rate log. */
    @Column(name = "source_name")
    private String sourceName;

    /** The adapter type of the owning source ({@code adzuna}, {@code jooble}, ...), for preference. */
    @Column(name = "source_type", length = 64)
    private String sourceType;

    /** The partner posting id, {@code <source>-<partnerId>}. */
    @Column(name = "job_id", nullable = false, length = 512)
    private String jobId;

    /** The member's apply link. Not indexed for the losing members; kept for a later task. */
    @Column(name = "apply_url", length = 4096)
    private String applyUrl;

    /** Whether the member publishes a salary (preference rule, first criterion). */
    @Column(name = "has_salary")
    private boolean hasSalary;

    /** The length of the member's description (preference rule, second criterion). */
    @Column(name = "description_length")
    private int descriptionLength;

    /** The member's posting expiry; past it the member no longer competes for the group. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /** The adapter posting (JSON), so any source's run can publish this member if it wins. */
    @Lob
    @Column(name = "payload")
    private String payload;
}
