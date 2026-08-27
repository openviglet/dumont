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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * One audit pass's orphan set for a source on a site (roadmap <strong>D68</strong>) — the ghosts
 * D64 could name but not remove, written down so an operator can read the evidence before anything
 * is deleted.
 *
 * <p>
 * The row <em>is</em> the review step. Deleting index documents on the strength of a single
 * discovery pass is what D33 and D34 exist to prevent; persisting the set instead lets the operator
 * see which ids, from which source, found when — and recognise a report produced by an unreachable
 * source rather than by content that genuinely went away.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Setter
@Getter
@Entity
@Table(name = "dum_index_orphan_report")
public class DumIndexOrphanReportModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Where a report is in the review it exists to force. */
    public enum ReportStatus {
        /** Found by an audit, awaiting an operator's decision. */
        PENDING,
        /** The operator applied it — the ids were deindexed. */
        APPLIED,
        /** The operator rejected the evidence; nothing was deleted. */
        DISCARDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    // T398 / §XIV.9 — Hibernate discriminator tenant id (per-tenant isolation).
    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;
    @Column
    private String source;
    @Column
    private String provider;
    @Column
    private String site;
    @Column
    private Locale locale;
    @Column
    private Instant discoveredAt;
    /**
     * How many documents the site held when the pass ran, so a reader can weigh the orphan count
     * against it — a report naming most of the index is a failed discovery pass, not a cleanup.
     */
    @Column
    private int indexedCount;
    @Column
    private Instant decidedAt;
    @Enumerated(EnumType.STRING)
    @Column
    private ReportStatus status;
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dum_index_orphan_report_id",
            joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "object_id", length = 500)
    private List<String> objectIds = new ArrayList<>();
}
