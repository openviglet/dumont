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
 * Cross-source entity resolution &amp; dedup (roadmap <strong>D18</strong>).
 *
 * <p>
 * Collapses the same real-world entity arriving from several sources — a course across institution
 * mirrors, one item under canonical + tracking URLs — into a single canonical document. A
 * {@link com.viglet.dumont.connector.commons.spi.structured.resolve.DumCanonicalKey} derives the
 * stable identity from grounded fields; a
 * {@link com.viglet.dumont.connector.commons.spi.structured.resolve.DumEntityResolver} accumulates
 * records across a crawl and folds those that share an id through the grounding-safe
 * {@link com.viglet.dumont.connector.commons.spi.structured.resolve.DumRecordMerge} (first
 * contributor wins, never overwrite, never invent), tagging each merged-in field with the source
 * that supplied it so the published provenance stays auditable.
 */
package com.viglet.dumont.connector.commons.spi.structured.resolve;
