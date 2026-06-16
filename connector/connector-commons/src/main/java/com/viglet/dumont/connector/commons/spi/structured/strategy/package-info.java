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

/**
 * Declarative extraction strategies (roadmap <strong>D02</strong>) for the structured-source SPI.
 *
 * <p>
 * Reusable building blocks so a new structured source is a strategy + config instead of a bespoke
 * crawler:
 * <ul>
 * <li>{@link com.viglet.dumont.connector.commons.spi.structured.strategy.DumSitemapStrategy} —
 * parse {@code sitemap.xml} (following nested indexes) and classify URLs into levels by pattern.</li>
 * <li>{@link com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlListingStrategy} —
 * follow detail anchors under a CSS selector on listing pages.</li>
 * <li>{@link com.viglet.dumont.connector.commons.spi.structured.strategy.DumTabularListingStrategy}
 * — parse {@code table}-style catalog listings (the SIGAA shape) where each row is a record.</li>
 * </ul>
 *
 * <p>
 * The two discovery strategies implement
 * {@link com.viglet.dumont.connector.commons.spi.structured.strategy.DumDiscoveryStrategy} and pair
 * with a {@link com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFieldExtractor}
 * (declarative {@link com.viglet.dumont.connector.commons.spi.structured.strategy.DumFieldRule}s) to
 * turn each discovered detail page into a schema-bound record. All fetching goes through the
 * {@link com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher} seam
 * ({@link com.viglet.dumont.connector.commons.spi.structured.strategy.DumJsoupFetcher} in
 * production, an in-memory fetcher in tests), and every value flows through the grounding contract
 * of {@link com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord}.
 */
package com.viglet.dumont.connector.commons.spi.structured.strategy;
