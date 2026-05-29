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

package com.viglet.dumont.connector.plugin.eds.export.bean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Flat, JSON-friendly snapshot of a {@code DumEDSSource} suitable for
 * import/export across environments. Collections of simple URL/string entities
 * are flattened to {@code Collection<String>}; structured ones (facets, field
 * mappings) keep their fields via dedicated exchange beans.
 */
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DumEDSSourceExchange {
    private String id;
    private String title;
    private String description;
    private Locale locale;
    private String localeClass;
    private String baseUrl;
    private String sitemapFallback;
    private String bodySelector;
    private String userAgent;
    private Integer fetchTimeoutMs;
    private Integer requestsPerSecond;
    private Integer maxConcurrent;
    private String facetPathPattern;

    @Builder.Default
    private Collection<String> turSNSites = new HashSet<>();

    @Builder.Default
    private Collection<String> indexes = new ArrayList<>();

    @Builder.Default
    private Collection<String> excludePatterns = new ArrayList<>();

    @Builder.Default
    private Collection<String> removeSelectors = new ArrayList<>();

    @Builder.Default
    private Collection<DumEDSFacetExchange> facets = new ArrayList<>();

    @Builder.Default
    private Collection<DumEDSFieldMappingExchange> fieldMappings = new ArrayList<>();
}
