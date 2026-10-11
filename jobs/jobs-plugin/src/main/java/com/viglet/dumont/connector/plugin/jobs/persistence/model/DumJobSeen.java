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
 * A job posting a source has fetched at least once and that has not expired yet (trabalharei
 * <strong>TRA13</strong>).
 *
 * <p>
 * Quota-bound partner APIs only return the newest postings on each run, while Dumont's run-end sweep
 * deindexes every id the run did not publish. This store is what keeps an older, still-open posting
 * alive between runs: {@code DumJobsStructuredSource} reports every unexpired row from
 * {@code discover()} and re-extracts it from {@link #payload} — the last record the adapter returned
 * — so the sweep sees it again without spending a partner call.
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
@Table(name = "job_seen",
        uniqueConstraints = @UniqueConstraint(columnNames = { "source_id", "job_id" }),
        indexes = @Index(name = "idx_job_seen_source_expires", columnList = "source_id, expires_at"))
public class DumJobSeen implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    /** The owning {@link DumJobSource#getId()}. */
    @Column(name = "source_id", nullable = false)
    private String sourceId;

    /** The document id the posting is published under. */
    @Column(name = "job_id", nullable = false, length = 512)
    private String jobId;

    /** After this instant the posting is no longer reported, so the next run deindexes it. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /**
     * When a run first fetched the posting (trabalharei <strong>TRA18</strong>) — the only date a
     * partner that sends none (BNE) can publish. Nullable so {@code ddl-auto: update} adds it to an
     * existing table; a row written before the column existed is backfilled from
     * {@link #lastSeenAt} the next time it is fetched.
     */
    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    /**
     * When a run last HEAD-checked the posting's apply link and got an answer (trabalharei
     * <strong>TRA24</strong>); {@code null} = never. The run samples the least recently checked
     * postings first. A check that hit a network error or a 5xx leaves it unchanged, so the posting
     * is retried on the next run.
     */
    @Column(name = "link_checked_at")
    private Instant linkCheckedAt;

    /**
     * When a run found the apply link dead (404/410) and expired the posting early (trabalharei
     * <strong>TRA24</strong>). The row is kept with {@link #expiresAt} at that instant, so the partner
     * returning the posting again does not revive it, and pruned 30 days after the partner last
     * returned it ({@link #lastSeenAt}).
     */
    @Column(name = "link_dead_at")
    private Instant linkDeadAt;

    /** The record (JSON object keyed by schema field names) as the adapter last returned it. */
    @Lob
    @Column(name = "payload")
    private String payload;
}
