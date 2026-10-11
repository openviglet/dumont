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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * One source record filed under a canonical id (roadmap <strong>D109</strong>), so cross-source
 * resolution survives a restart. {@link #record} is the record as {@code DumRecordCodec} writes it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "dum_resolution_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_dum_resolution_member",
                columnNames = { "provider", "canonical_id", "source", "record_id" }),
        indexes = {
                @Index(name = "ix_dum_resolution_member_canonical", columnList = "provider, canonical_id"),
                @Index(name = "ix_dum_resolution_member_source", columnList = "provider, source, record_id") })
public class DumResolutionMemberModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "canonical_id", nullable = false, length = 512)
    private String canonicalId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "record_id", nullable = false, length = 512)
    private String recordId;

    @Column(name = "live", nullable = false)
    private boolean live;

    /** When the member was first filed, relative to the provider's others. */
    @Column(name = "member_order", nullable = false)
    private long memberOrder;

    @Lob
    @Column(name = "record_json", nullable = false)
    private String record;
}
