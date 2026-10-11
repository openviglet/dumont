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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/** Grounding-safety and failure-isolation guarantees of {@link DumEnrichmentChain}. */
class DumEnrichmentChainTest {

    private final DumStructuredSchema schema = DumStructuredSchema.builder()
            .text("title")
            .facet("area")
            .string("region")
            .build();

    private final DumStructuredSource source = new StubSource(schema);

    private DumStructuredRecord baseRecord() {
        return DumStructuredRecord.builder("r1", schema)
                .set("title", "Algorithms")
                .set("area", "Computer Science")
                .build();
    }

    @Test
    void emptyChainReturnsTheSameInstance() {
        DumStructuredRecord rec = baseRecord();
        assertTrue(DumEnrichmentChain.empty().isEmpty());
        assertSame(rec, DumEnrichmentChain.empty().apply(source, rec));
    }

    @Test
    void mergesContributionsThroughTheSchemaBoundBuilder() {
        DumEnricher derive = enricher("derive", r -> Map.of("region", "Sudeste"));
        DumStructuredRecord out = DumEnrichmentChain.of(derive).apply(source, baseRecord());
        assertEquals("Sudeste", out.values().get("region"));
        assertEquals("Algorithms", out.values().get("title"));
    }

    @Test
    void undeclaredFieldContributionIsRejectedRecordUnchanged() {
        DumEnricher rogue = enricher("rogue", r -> Map.of("ghost", "x"));
        DumStructuredRecord in = baseRecord();
        DumStructuredRecord out = DumEnrichmentChain.of(rogue).apply(source, in);
        assertFalse(out.has("ghost"), "an undeclared field can never be injected");
        assertEquals("Algorithms", out.values().get("title"));
    }

    @Test
    void oneInvalidContributionDropsOnlyItsOwnField() {
        // D102: a multi-field enricher keeps its good fields when one of them is bad.
        DumEnricher salary = enricher("salary", r -> Map.of("region", "Sudeste", "ghost", "x"));
        DumStructuredRecord out = DumEnrichmentChain.of(salary).apply(source, baseRecord());
        assertEquals("Sudeste", out.values().get("region"), "the valid contribution survives");
        assertFalse(out.has("ghost"));
        assertEquals("Algorithms", out.values().get("title"));
    }

    @Test
    void blankContributionCannotEraseASourcedValue() {
        DumEnricher blanker = enricher("blanker", r -> Map.of("area", "   "));
        DumStructuredRecord out = DumEnrichmentChain.of(blanker).apply(source, baseRecord());
        assertEquals(List.of("Computer Science"), out.values().get("area"),
                "a blank override is grounded away; the sourced value survives");
    }

    @Test
    void aThrowingEnricherIsIsolatedAndTheChainContinues() {
        DumEnricher boom = enricher("boom", r -> {
            throw new IllegalStateException("kaboom");
        });
        DumEnricher derive = enricher("derive", r -> Map.of("region", "Nordeste"));
        DumStructuredRecord out = DumEnrichmentChain.of(boom, derive).apply(source, baseRecord());
        assertEquals("Nordeste", out.values().get("region"),
                "a failing enricher must not stop the ones after it");
        assertEquals("Algorithms", out.values().get("title"));
    }

    @Test
    void laterEnricherSeesEarlierContribution() {
        DumEnricher first = enricher("first", r -> Map.of("region", "SP"));
        DumEnricher second = enricher("second",
                r -> r.has("region") ? Map.of("title", "tagged") : Map.of());
        DumStructuredRecord out = DumEnrichmentChain.of(first, second).apply(source, baseRecord());
        assertEquals("tagged", out.values().get("title"));
    }

    private static DumEnricher enricher(String name,
            java.util.function.Function<DumStructuredRecord, Map<String, Object>> fn) {
        return new DumEnricher() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
                return fn.apply(rec);
            }
        };
    }

    private record StubSource(DumStructuredSchema schema) implements DumStructuredSource {
        @Override
        public String getSourceName() {
            return "stub";
        }

        @Override
        public String getProviderName() {
            return "STUB";
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of("Sample");
        }

        @Override
        public List<String> discover() {
            return List.of();
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            return Optional.empty();
        }

        @Override
        public Locale getLocale() {
            return Locale.US;
        }
    }
}
