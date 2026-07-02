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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "eds_source")
public class DumEDSSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    public static final String DUM_EDS_SOURCE = "dumEDSSource";
    public static final String SOURCE_ID = "source_id";
    public static final String EDS_SN_SITE = "eds_sn_site";
    public static final String SN_SITE = "sn_site";

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    @Column
    private String title;

    @Column
    private String description;

    @Column
    private Locale locale;

    /**
     * Optional fully-qualified class name implementing
     * {@link com.viglet.dumont.connector.eds.commons.ext.DumEDSExtLocaleInterface}.
     * When {@link #locale} is null, the plugin process resolves locale per page
     * by instantiating this class via {@code DumCustomClassCache} and calling
     * its {@code consume(DumEDSContext)} method.
     */
    @Column(name = "locale_class")
    private String localeClass;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "sitemap_fallback")
    private String sitemapFallback;

    @Column(name = "body_selector")
    private String bodySelector;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "fetch_timeout_ms")
    private Integer fetchTimeoutMs;

    @Column(name = "requests_per_second")
    private Integer requestsPerSecond;

    @Column(name = "max_concurrent")
    private Integer maxConcurrent;

    /**
     * Pattern for deriving facets from the page path. Supports {@code {name}}
     * for named groups and {@code **} to swallow remaining segments. Example:
     * {@code /{locale}/{audience}/{section}/**}. The named groups must
     * correspond by {@code name} to entries in {@link #facets}.
     */
    @Column(name = "facet_path_pattern")
    private String facetPathPattern;

    @Builder.Default
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = EDS_SN_SITE, joinColumns = @JoinColumn(name = SOURCE_ID))
    @Column(name = SN_SITE, nullable = false)
    private Collection<String> turSNSites = new HashSet<>();

    /**
     * Indexes are kept in a {@link LinkedHashSet} so the pipeline iterates them in
     * config declaration order in-memory (tests, edit/create round-trips). JPA load
     * order is still Set-typed and ordering must not be relied upon across restarts;
     * Phase 4+ may add an @OrderColumn if a stable on-disk ordering becomes load-bearing.
     */
    @Builder.Default
    @OneToMany(mappedBy = DUM_EDS_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY,
            cascade = { CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<DumEDSIndex> indexes = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = DUM_EDS_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY,
            cascade = { CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<DumEDSExcludePattern> excludePatterns = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = DUM_EDS_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY,
            cascade = { CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<DumEDSRemoveSelector> removeSelectors = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = DUM_EDS_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY,
            cascade = { CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<DumEDSFacet> facets = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = DUM_EDS_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY,
            cascade = { CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<DumEDSFieldMapping> fieldMappings = new LinkedHashSet<>();

    public void setIndexes(Collection<DumEDSIndex> indexes) {
        this.indexes.clear();
        if (indexes != null) {
            this.indexes.addAll(indexes);
        }
    }

    public void setExcludePatterns(Collection<DumEDSExcludePattern> excludePatterns) {
        this.excludePatterns.clear();
        if (excludePatterns != null) {
            this.excludePatterns.addAll(excludePatterns);
        }
    }

    public void setRemoveSelectors(Collection<DumEDSRemoveSelector> removeSelectors) {
        this.removeSelectors.clear();
        if (removeSelectors != null) {
            this.removeSelectors.addAll(removeSelectors);
        }
    }

    public void setFacets(Collection<DumEDSFacet> facets) {
        this.facets.clear();
        if (facets != null) {
            this.facets.addAll(facets);
        }
    }

    public void setFieldMappings(Collection<DumEDSFieldMapping> fieldMappings) {
        this.fieldMappings.clear();
        if (fieldMappings != null) {
            this.fieldMappings.addAll(fieldMappings);
        }
    }

    public void setTurSNSites(Collection<String> turSNSites) {
        this.turSNSites.clear();
        if (turSNSites != null) {
            this.turSNSites.addAll(turSNSites);
        }
    }
}
