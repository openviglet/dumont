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
 * Pluggable enrichment stage (roadmap <strong>D04</strong>).
 *
 * <p>
 * A hook in the structured-source indexing chain that derives or normalizes fields after extraction
 * and before publish. A {@link com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher}
 * returns field contributions; the
 * {@link com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain} merges them
 * back through the schema-bound record builder, so the grounding contract is enforced for free —
 * enrichment may normalize, classify or derive, but can never inject an undeclared field, publish an
 * empty value, erase a sourced value, or fabricate one.
 *
 * <p>
 * Two ready enrichers cover the roadmap's examples:
 * {@link com.viglet.dumont.connector.commons.spi.structured.enrich.DumNormalizationEnricher} (a
 * deterministic, always-on derive/normalize — canonical taxonomy mapping, region derivation) and
 * {@link com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassificationEnricher} (an
 * LLM-classified label, behind config + a
 * {@link com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentCache}). The LLM call
 * itself sits behind the
 * {@link com.viglet.dumont.connector.commons.spi.structured.enrich.DumClassifier} seam, so this
 * module carries no model wiring; the production classifier in {@code connector-app} is a thin
 * adapter over Turing's existing LLM surface.
 *
 * <p>
 * Enables index-time embeddings (D17), cross-source entity resolution (D18) and the reusable
 * taxonomy library (D19), which all run as enrichment stages over sourced data.
 */
package com.viglet.dumont.connector.commons.spi.structured.enrich;
