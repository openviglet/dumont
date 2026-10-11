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
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tombstone waiting to reach the job site (trabalharei <strong>TRA24</strong>): a published
 * document left the index, and the site is told so it can answer its old URL with "vaga encerrada"
 * rather than a 404. A row exists only while the POST has not succeeded; it is deleted once the site
 * accepts it, and retried on every run until then. One row per document id: a document that vanishes
 * again before its tombstone went out replaces the pending body.
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
@Table(name = "job_tombstone", uniqueConstraints = @UniqueConstraint(columnNames = "document_id"))
public class DumJobTombstone implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    /** The id the document was published under ({@code job-<hash>} or {@code adzuna-123}). */
    @Column(name = "document_id", nullable = false, length = 512)
    private String documentId;

    /** The JSON body to POST, built when the document vanished. */
    @Lob
    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    /** Failed POSTs so far. */
    @Column(name = "attempts")
    private int attempts;

    /** The last failure, already scrubbed of the token. */
    @Column(name = "last_error", length = 1024)
    private String lastError;
}
