/*
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

package com.viglet.dumont.connector.plugin.eds.api.dto;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSExcludePattern;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFacet;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSFieldMapping;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSIndex;
import com.viglet.dumont.connector.plugin.eds.persistence.model.DumEDSRemoveSelector;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for {@code DumEDSSourceApi} create/update (SonarQube S4684) — an aggregate POJO
 * mirroring the writable fields of {@code DumEDSSource} so the persistent root entity is never bound
 * directly from the request body. The child collections stay typed as their config sub-entities
 * (intra-aggregate rows the controller re-parents on save); only the root binding is decoupled.
 * {@code id} is accepted only to validate the path variable on update.
 */
@Getter
@Setter
@NoArgsConstructor
public class DumEDSSourceRequest {

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
    private Collection<String> turSNSites = new HashSet<>();
    private Collection<DumEDSIndex> indexes = new HashSet<>();
    private Collection<DumEDSExcludePattern> excludePatterns = new HashSet<>();
    private Collection<DumEDSRemoveSelector> removeSelectors = new HashSet<>();
    private Collection<DumEDSFacet> facets = new HashSet<>();
    private Collection<DumEDSFieldMapping> fieldMappings = new HashSet<>();
}
