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
 * Shared, cross-cutting normalizers (roadmap <strong>D26</strong>).
 *
 * <p>
 * The AEM extraction god-class fused not only business derivations but a handful of
 * <em>cross-cutting normalizations</em> into extraction — timezone-aware date handling, HTML
 * stripping, Portuguese month-name formatting. Track H pulls those out so they are stateless, pure
 * and reusable across sources, exactly like the
 * {@link com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher enrichers} that
 * consume them.
 *
 * <p>
 * Every helper here is a pure function (no I/O, no hidden clock — a reference instant is always
 * passed in) returning {@link java.util.Optional} so an un-normalizable input yields no value rather
 * than a fabricated one, keeping the grounding contract intact. They are the building blocks the
 * derivation enrichers and the per-source extractors share:
 * {@link com.viglet.dumont.connector.commons.spi.structured.normalize.DumTextNormalizers} (first
 * letter / accent folding, HTML&rarr;text) and
 * {@link com.viglet.dumont.connector.commons.spi.structured.normalize.DumDateNormalizers}
 * (PT month names, day-of-today in a zone, formatted date ranges).
 */
package com.viglet.dumont.connector.commons.spi.structured.normalize;
