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

package com.viglet.dumont.connector.commons.spi.structured.enrich;

import java.util.Map;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * A pluggable enrichment stage (roadmap <strong>D04</strong>): a hook in the indexing chain that
 * <em>derives</em> or <em>normalizes</em> fields after extraction and before publish.
 *
 * <p>
 * An enricher returns a map of field contributions — canonical taxonomy values, a region derived
 * from a state code, an LLM-classified label for a thin description, an embedding vector, and so on.
 * The {@link DumEnrichmentChain} merges those contributions back into the record <em>through the
 * schema-bound {@link DumStructuredRecord.Builder}</em>, so the grounding contract is enforced for
 * free: a contribution to a field the schema does not declare is rejected, a blank or empty value is
 * dropped (the field stays absent), and an enricher can never blank-out a value the source already
 * supplied.
 *
 * <p>
 * <strong>Grounding contract.</strong> Enrichment may normalize, classify or derive — it must
 * <em>never</em> fabricate a sourced attribute. When an enricher cannot ground a value (no taxonomy
 * match, the classifier is unsure, the LLM call failed) it returns no contribution for that field
 * rather than inventing one. This mirrors cursarei's {@code enrich_course} / {@code llm_area} step.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface DumEnricher {

    /** A short, stable name for logging and ordering diagnostics. */
    String name();

    /**
     * Compute the field contributions for one record. The returned map is keyed by field name; each
     * value is merged into the record via {@link DumStructuredRecord.Builder#set(String, Object)},
     * which validates the field against the schema and grounds the value. Return an empty map (never
     * {@code null} is required, but it is tolerated) when this enricher has nothing to add.
     *
     * <p>
     * Implementations must not throw to signal "nothing to contribute" — return an empty map. A
     * thrown exception is caught by the chain and the record is left unchanged, so enrichment is
     * never a single point of failure for a run.
     */
    Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord record);

    /** Whether this enricher runs for the given source. Defaults to all sources. */
    default boolean appliesTo(DumStructuredSource source) {
        return true;
    }
}
