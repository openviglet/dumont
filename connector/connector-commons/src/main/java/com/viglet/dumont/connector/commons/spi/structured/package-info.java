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
 * Structured-source SPI (roadmap <strong>D01</strong>).
 *
 * <p>
 * A first-class way to model sources whose value is their <em>structure</em> — tabular listings,
 * per-item detail pages with a typed schema — above the generic web-crawler link-spider. A source
 * author implements {@link com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource}
 * ({@code discover()} + {@code extract(id)} + a declared
 * {@link com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema}); the generic
 * indexing loop and grounding contract live in
 * {@link com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin}, which
 * implements {@link com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin}.
 */
package com.viglet.dumont.connector.commons.spi.structured;
