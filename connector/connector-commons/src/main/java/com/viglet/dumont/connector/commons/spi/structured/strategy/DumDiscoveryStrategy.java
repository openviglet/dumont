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

package com.viglet.dumont.connector.commons.spi.structured.strategy;

import java.util.List;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * A reusable way to enumerate the detail-page URLs a structured source should index — the
 * <em>discover</em> half of the {@link DumStructuredSource} contract, shipped as configuration
 * instead of a bespoke crawler (roadmap <strong>D02</strong>).
 *
 * <p>
 * Two implementations cover the common cases: {@link DumSitemapStrategy} (parse {@code sitemap.xml},
 * classify URLs by pattern) and {@link DumHtmlListingStrategy} (follow anchors under a CSS selector
 * on listing pages). A source delegates its {@code discover()} to one of these and pairs it with a
 * {@link DumHtmlFieldExtractor} for the <em>extract</em> half. The third strategy,
 * {@link DumTabularListingStrategy}, instead does both — each table row is a self-contained record.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumDiscoveryStrategy {

    /**
     * The discovered detail-page URLs, in discovery order, de-duplicated. Each value is suitable as
     * the content id handed back to {@code extract}.
     */
    default List<String> discover() {
        return discoverWithLevels().stream().map(DumDiscoveredUrl::url).toList();
    }

    /** The discovered URLs together with the level each was classified into. */
    List<DumDiscoveredUrl> discoverWithLevels();
}
