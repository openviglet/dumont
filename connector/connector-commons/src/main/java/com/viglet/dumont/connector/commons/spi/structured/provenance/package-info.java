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
 * Field-level provenance (roadmap <strong>D16</strong>) for the structured-source SPI.
 *
 * <p>
 * Elevates the grounding contract from "omit absent" to "every present field is traceable to a
 * source". Two halves, both by-products of a normal index run rather than a second pass:
 * <ul>
 * <li><strong>Per-document origin</strong> —
 * {@link com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance} (strategy,
 * selector/path, confidence) is attached to a field as it is set on a
 * {@link com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord}, then emitted as a
 * compact reserved attribute on every {@code TurSNJobItem} by
 * {@link com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenance}, so the
 * search/RAG side can cite and audit each attribute.</li>
 * <li><strong>Last-seen across runs</strong> — each full {@code indexAll} appends a
 * {@link com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceSnapshot} to a
 * per-source {@link com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceHistoryStore},
 * and {@link com.viglet.dumont.connector.commons.spi.structured.provenance.DumProvenanceMonitor}
 * reads the series back so a field a source stopped declaring is visible as last-seen at run
 * {@code N}.</li>
 * </ul>
 *
 * <p>
 * Mirrors the D14 preview-snapshot and D15 coverage packages: a commons-side seam with an in-memory
 * default ({@link com.viglet.dumont.connector.commons.spi.structured.provenance.DumInMemoryProvenanceHistoryStore})
 * and a persisted implementation supplied by {@code connector-app}.
 */
package com.viglet.dumont.connector.commons.spi.structured.provenance;
