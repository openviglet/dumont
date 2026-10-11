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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.provenance.DumFieldProvenance;

/** Where an enriched value came from, recorded in its provenance (roadmap D101). */
class DumEnricherProvenanceTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .string("contract").string("salary").facet("skills").build();

    @Test
    void aSourcedContributionRecordsItsSelectorAndConfidence() {
        DumStructuredRecord rec = DumStructuredRecord.builder("1", SCHEMA).build();

        DumStructuredRecord enriched = apply(rec, Map.of("contract", DumSourced.of("CLT", "title", 0.8)));

        DumFieldProvenance provenance = enriched.provenance().get("contract");
        assertEquals("CLT", enriched.values().get("contract"));
        assertEquals("enrichment:contract", provenance.strategy());
        assertEquals("title", provenance.selector());
        assertEquals(0.8, provenance.confidence());
    }

    @Test
    void aPassThroughKeepsTheSourcesProvenance() {
        // The partner's salary, only repeated by the enricher, still cites the partner.
        DumFieldProvenance partner = DumFieldProvenance.json("salary_min");
        DumStructuredRecord rec = DumStructuredRecord.builder("1", SCHEMA)
                .set("salary", "5000", partner).set("skills", List.of("java"), partner).build();

        DumStructuredRecord plain = apply(rec, Map.of("salary", "5000", "skills", "java"));
        DumStructuredRecord sourced = apply(rec, Map.of("salary", DumSourced.of("5000", "salary_min", 1.0)));

        assertEquals(partner, plain.provenance().get("salary"));
        assertEquals(partner, plain.provenance().get("skills"));
        assertEquals(partner, sourced.provenance().get("salary"));
    }

    @Test
    void aChangedValueIsTaggedToTheEnricher() {
        DumStructuredRecord rec = DumStructuredRecord.builder("1", SCHEMA)
                .set("salary", "5k", DumFieldProvenance.json("salary")).build();

        DumStructuredRecord enriched = apply(rec, Map.of("salary", "5000"));

        assertEquals("5000", enriched.values().get("salary"));
        assertEquals("enrichment:contract", enriched.provenance().get("salary").strategy());
        assertNull(enriched.provenance().get("salary").selector());
    }

    @Test
    void aConfidenceOutsideZeroToOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DumSourced.of("CLT", "title", 1.5));
    }

    private static DumStructuredRecord apply(DumStructuredRecord rec, Map<String, Object> contribution) {
        DumEnricher enricher = new DumEnricher() {
            @Override
            public String name() {
                return "contract";
            }

            @Override
            public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord r) {
                return contribution;
            }
        };
        return DumEnrichmentChain.of(enricher).apply(null, rec);
    }
}
