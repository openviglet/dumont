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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;

class DumNormalizationEnricherTest {

    private final DumStructuredSchema schema = DumStructuredSchema.builder()
            .string("uf")
            .string("region")
            .facet("rawAreas")
            .facet("areas")
            .build();

    @Test
    void mapsASingleValueThroughTheLookupTable() {
        DumNormalizationEnricher enricher = DumNormalizationEnricher.lookup(
                "region", "uf", "region", Map.of("SP", "Sudeste"));

        DumStructuredRecord record = DumStructuredRecord.builder("r", schema).set("uf", "SP").build();
        assertEquals(Map.of("region", "Sudeste"), enricher.enrich(null, record));
    }

    @Test
    void mapsMultiValuedSourceAndDropsUnmappedEntries() {
        DumNormalizationEnricher enricher = DumNormalizationEnricher.lookup(
                "areas", "rawAreas", "areas",
                Map.of("cs", "Computer Science", "math", "Mathematics"));

        DumStructuredRecord record = DumStructuredRecord.builder("r", schema)
                .set("rawAreas", List.of("cs", "unknown", "math"))
                .build();

        Object areas = enricher.enrich(null, record).get("areas");
        assertEquals(List.of("Computer Science", "Mathematics"), areas,
                "unmapped values are omitted, never replaced with a placeholder");
    }

    @Test
    void absentSourceFieldContributesNothing() {
        DumNormalizationEnricher enricher = DumNormalizationEnricher.lookup(
                "region", "uf", "region", Map.of("SP", "Sudeste"));
        DumStructuredRecord record = DumStructuredRecord.builder("r", schema).set("uf", "XX").build();
        assertTrue(enricher.enrich(null, record).isEmpty(), "no mapping -> no contribution");

        DumStructuredRecord noUf = DumStructuredRecord.builder("r", schema).set("region", "x").build();
        assertTrue(enricher.enrich(null, noUf).isEmpty(), "absent source field -> no contribution");
    }
}
