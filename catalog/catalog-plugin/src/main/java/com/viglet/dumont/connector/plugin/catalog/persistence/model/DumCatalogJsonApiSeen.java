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
 * When an incremental JSON API catalog source last saw one record (roadmap <strong>D92</strong>);
 * a record unseen for longer than the manifest's expiry is deindexed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "catalog_json_api_seen",
        indexes = @Index(name = "idx_catalog_json_api_seen", columnList = "source_name,object_id"))
public class DumCatalogJsonApiSeen implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "object_id", nullable = false, length = 1000)
    private String objectId;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;
}
