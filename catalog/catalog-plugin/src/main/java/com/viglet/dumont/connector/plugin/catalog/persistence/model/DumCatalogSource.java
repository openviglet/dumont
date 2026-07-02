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
import java.util.Collection;
import java.util.HashSet;

import com.viglet.dumont.connector.plugin.catalog.migration.DumCatalogMigrationState;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A catalog-bridge source (roadmap <strong>D10</strong>): one institution's already-normalized
 * catalog, served as JSON by the external (Python) extraction engine. The bridge fetches
 * {@link #catalogUrl} and republishes each record to Turing under the grounding contract — it never
 * crawls or extracts, only re-indexes what the engine already produced.
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
@Table(name = "catalog_source")
public class DumCatalogSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    @Column
    private String name;

    @Column
    private String description;

    /** URL serving this institution's normalized catalog JSON (array, or {@code {courses:[...]}}). */
    @Column(name = "catalog_url", length = 2000)
    private String catalogUrl;

    /** Optional request header sent when fetching {@link #catalogUrl} (e.g. an API key header). */
    @Column
    private String authHeaderName;

    @Column
    private String authHeaderValue;

    @Column
    private String locale;

    /**
     * Which indexing leg this source runs on (roadmap <strong>D11</strong>). Defaults to
     * {@link DumCatalogMigrationState#SIDECAR}; flips to {@code NATIVE} only after a passing parity
     * gate. A {@code null} value (legacy rows) is read as {@code SIDECAR}.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "migration_state", length = 16)
    private DumCatalogMigrationState migrationState = DumCatalogMigrationState.SIDECAR;

    /** The migration leva this source belongs to (e.g. {@code "sigaa-federals"}), for grouping. */
    @Column
    private String leva;

    /** The recipe ref this source binds to when {@code NATIVE} (e.g. {@code "sigaa-federal"}). */
    @Column(name = "native_recipe")
    private String nativeRecipe;

    /** The public listing URL the native recipe crawls when {@code NATIVE}. */
    @Column(name = "native_listing_url", length = 2000)
    private String nativeListingUrl;

    @Builder.Default
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "catalog_sn_site", joinColumns = @JoinColumn(name = "source_id"))
    @Column(name = "sn_site", nullable = false)
    private Collection<String> turSNSites = new HashSet<>();
}
