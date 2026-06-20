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

import org.hibernate.annotations.TenantId;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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
 * One full index run's per-field provenance (roadmap <strong>D16</strong>) — appended to a source's
 * history at the end of each {@code indexAll}, so the origin of each field and when it was last seen
 * survive a restart and stay queryable across runs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Setter
@Getter
@Entity
@Table(name = "dum_provenance_run")
public class DumProvenanceRunModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
    private Instant capturedAt;
    @Column
    private int documentCount;
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dum_provenance_run_field", joinColumns = @JoinColumn(name = "run_id"))
    private List<DumProvenanceFieldModel> fields = new ArrayList<>();
}
