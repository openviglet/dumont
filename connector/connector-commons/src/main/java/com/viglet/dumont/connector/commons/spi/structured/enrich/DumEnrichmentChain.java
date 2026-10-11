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

import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;

import lombok.extern.slf4j.Slf4j;

/**
 * An ordered list of {@link DumEnricher}s applied to a record between extraction and publish
 * (roadmap <strong>D04</strong>).
 *
 * <p>
 * Each enricher's contributions are merged back into the record by rebuilding it through the
 * schema-bound {@link DumStructuredRecord.Builder}. That single design choice gives the chain its
 * two guarantees:
 *
 * <ul>
 * <li><strong>Grounding-safe by construction.</strong> A contribution flows through
 * {@link DumStructuredRecord.Builder#set(String, Object)}, so it must target a declared field, must
 * satisfy that field's type, and a blank/empty contribution is dropped. An enricher cannot inject an
 * undeclared field, publish an empty value, or erase a value the source already supplied (the source
 * value is set first, and a blank override is grounded away). Existing values win unless an enricher
 * supplies a non-blank replacement.</li>
 * <li><strong>Never a single point of failure.</strong> An enricher that throws, or that produces an
 * invalid contribution, is logged and skipped — the record continues down the chain unchanged rather
 * than failing the whole run. A flaky LLM enricher degrades to "no classified field", not "no
 * document".</li>
 * </ul>
 *
 * <p>
 * The chain is immutable; build one per plugin and reuse it across records and runs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumEnrichmentChain {

    private static final DumEnrichmentChain EMPTY = new DumEnrichmentChain(List.of());

    private final List<DumEnricher> enrichers;

    private DumEnrichmentChain(List<DumEnricher> enrichers) {
        this.enrichers = List.copyOf(enrichers);
    }

    /** A chain that does nothing — the default when a plugin declares no enrichment. */
    public static DumEnrichmentChain empty() {
        return EMPTY;
    }

    public static DumEnrichmentChain of(DumEnricher... enrichers) {
        return new DumEnrichmentChain(List.of(enrichers));
    }

    public static DumEnrichmentChain of(List<DumEnricher> enrichers) {
        return enrichers == null || enrichers.isEmpty() ? EMPTY : new DumEnrichmentChain(enrichers);
    }

    public boolean isEmpty() {
        return enrichers.isEmpty();
    }

    /** The enrichers in this chain, in order — immutable. Lets a caller compose chains. */
    public List<DumEnricher> enrichers() {
        return enrichers;
    }

    /**
     * Run every applicable enricher in order, returning the enriched record. The input record is
     * never mutated; a new record is returned only when an enricher actually contributes. When the
     * chain is empty the input is returned as-is.
     */
    public DumStructuredRecord apply(DumStructuredSource source, DumStructuredRecord rec) {
        DumStructuredRecord current = rec;
        for (DumEnricher enricher : enrichers) {
            current = applyOne(enricher, source, current);
        }
        return current;
    }

    private DumStructuredRecord applyOne(DumEnricher enricher, DumStructuredSource source,
            DumStructuredRecord rec) {
        if (!enricher.appliesTo(source)) {
            return rec;
        }
        Map<String, Object> contributions;
        try {
            contributions = enricher.enrich(source, rec);
        } catch (RuntimeException e) {
            log.warn("Enricher '{}' failed on record '{}' in source '{}' — record left unchanged",
                    enricher.name(), rec.id(), source.getSourceName(), e);
            return rec;
        }
        if (contributions == null || contributions.isEmpty()) {
            return rec;
        }
        return merge(enricher, rec, contributions);
    }

    private DumStructuredRecord merge(DumEnricher enricher, DumStructuredRecord rec,
            Map<String, Object> contributions) {
        try {
            // Source values first, each keeping its origin (D16), and the record's transients (D97);
            // a non-blank contribution then overrides — and re-tags the field's provenance to this
            // enricher — while a blank one is dropped (the source value and its provenance survive).
            DumStructuredRecord.Builder builder = rec.toBuilder();
            DumFieldProvenance enriched = DumFieldProvenance.enrichment(enricher.name());
            contributions.forEach((field, value) -> {
                if (value instanceof DumTransient working) {
                    builder.transientValue(field, working.value());
                } else if (value == DumClear.FIELD) {
                    // D98: an explicit clear removes the source's value; a blank one never does.
                    builder.clear(field);
                } else {
                    builder.set(field, value, enriched);
                }
            });
            return builder.build();
        } catch (RuntimeException e) {
            log.warn("Enricher '{}' produced an invalid contribution for record '{}' "
                    + "(undeclared field or type mismatch) — record left unchanged: {}",
                    enricher.name(), rec.id(), e.getMessage());
            return rec;
        }
    }
}
