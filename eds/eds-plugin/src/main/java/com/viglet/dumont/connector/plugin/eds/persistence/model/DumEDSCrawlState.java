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
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-(source, path) bookkeeping used for incremental crawls. Records the
 * {@code lastModified} stamp last observed for the page and the {@code
 * checksum} produced the last time we built a TurSNJobItem for it. Two uses:
 *
 * <ul>
 *   <li><strong>Skip fetch on no-op</strong>: when the discovery row carries a
 *       {@code lastModified} equal to the stored value, the HTTP fetch is
 *       skipped entirely on the next crawl.</li>
 *   <li><strong>Removal detection</strong>: rows whose paths disappear from
 *       discovery in a later crawl get a DELETE TurSNJobItem and their state
 *       is purged.</li>
 * </ul>
 *
 * <p>The (source, path) pair is unique — enforced via a composite UNIQUE
 * constraint and the {@code path} column is indexed for the typical lookup.
 */
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "eds_crawl_state",
        uniqueConstraints = @UniqueConstraint(columnNames = { "eds_source_id", "path" }),
        indexes = @Index(name = "idx_eds_crawl_state_source", columnList = "eds_source_id"))
@JsonIgnoreProperties({ "dumEDSSource" })
public class DumEDSCrawlState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    @ManyToOne
    @JoinColumn(name = "eds_source_id", nullable = false)
    private DumEDSSource dumEDSSource;

    @Column(nullable = false, length = 2048)
    private String path;

    @Column(name = "last_modified")
    private String lastModified;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;
}
