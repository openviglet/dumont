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

package com.viglet.dumont.connector.eds.commons.domain;

import java.util.Collections;
import java.util.Map;

/**
 * A page path discovered during the discovery phase, paired with the metadata
 * EDS already exposed in its sheet (the per-row map). The metadata snapshot is
 * the source-of-truth-of-record for fields when the JSON has them; HTML fetch
 * fills the gaps and overrides on conflict.
 */
public record DumEDSDiscoveredPage(String path, Map<String, String> metadata, String origin) {

    public static final String ORIGIN_INDEX = "INDEX";
    public static final String ORIGIN_SITEMAP = "SITEMAP";

    public DumEDSDiscoveredPage {
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(metadata);
    }

    public static DumEDSDiscoveredPage fromIndex(String path, Map<String, String> metadata) {
        return new DumEDSDiscoveredPage(path, metadata, ORIGIN_INDEX);
    }

    public static DumEDSDiscoveredPage fromSitemap(String path) {
        return new DumEDSDiscoveredPage(path, Map.of(), ORIGIN_SITEMAP);
    }
}
