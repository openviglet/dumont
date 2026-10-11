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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/** The built-in fallback and one-to-many enricher shapes (roadmap D103). */
class DumEnricherShapesTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .string("contractType").text("title").text("description").string("contract")
            .field(DumStructuredField.builder("salaryMin").type(TurSEFieldType.DOUBLE).build())
            .field(DumStructuredField.builder("salaryMax").type(TurSEFieldType.DOUBLE).build())
            .string("salaryPeriod")
            .build();

    private static final DumFallbackEnricher CONTRACT = DumFallbackEnricher.builder("contract", "contract")
            .input("contractType", 1.0).input("title", 0.8).input("description", 0.6)
            .parser(DumEnricherShapesTest::contract)
            .build();

    @Test
    void theFirstInputThatDecidesWinsAndIsNamedInTheProvenance() {
        DumStructuredRecord fromTitle = DumStructuredRecord.builder("1", SCHEMA)
                .set("contractType", "full time").set("title", "Analista PJ")
                .set("description", "Vaga CLT").build();

        DumStructuredRecord out = DumEnrichmentChain.of(CONTRACT).apply(null, fromTitle);

        assertEquals("PJ", out.values().get("contract"), "the partner's field did not decide, the title did");
        assertEquals("title", out.provenance().get("contract").selector());
        assertEquals(0.8, out.provenance().get("contract").confidence());
    }

    @Test
    void aTransientInputCountsAndNoDecisionContributesNothing() {
        DumStructuredRecord transientInput = DumStructuredRecord.builder("1", SCHEMA)
                .set("title", "Analista").transientValue("description", "contrato CLT").build();
        DumStructuredRecord undecided = DumStructuredRecord.builder("2", SCHEMA)
                .set("title", "Analista").build();

        assertEquals("CLT", DumEnrichmentChain.of(CONTRACT).apply(null, transientInput).values().get("contract"));
        assertFalse(DumEnrichmentChain.of(CONTRACT).apply(null, undecided).has("contract"),
                "no input decided, so nothing is invented");
    }

    @Test
    void anInputTheStopRuleAcceptsEndsTheFallbackWithNothing() {
        // D117: "CLT ou PJ" settles that the contract is unknown; the description is not read to guess.
        DumFallbackEnricher stopping = DumFallbackEnricher.builder("contract", "contract")
                .input("title", 0.8).input("description", 0.6)
                .parser(DumEnricherShapesTest::contract)
                .stopOn(text -> text.contains(" ou "))
                .build();
        DumStructuredRecord ambiguous = DumStructuredRecord.builder("1", SCHEMA)
                .set("title", "Analista CLT ou PJ").set("description", "Vaga CLT").build();
        DumStructuredRecord clear = DumStructuredRecord.builder("2", SCHEMA)
                .set("title", "Analista").set("description", "Vaga CLT").build();

        assertFalse(DumEnrichmentChain.of(stopping).apply(null, ambiguous).has("contract"));
        assertEquals("CLT", DumEnrichmentChain.of(stopping).apply(null, clear).values().get("contract"));
    }

    @Test
    void oneInputSplitsIntoSeveralFieldsAndABadOneStaysAlone() {
        DumSplitEnricher salary = DumSplitEnricher.of("salary", "salaryText", text -> {
            Map<String, Object> parsed = new HashMap<>();
            parsed.put("salaryMin", 3000.0);
            parsed.put("salaryMax", text.contains("?") ? "unknown" : 5000.0);
            parsed.put("salaryPeriod", "month");
            parsed.put("ignored", null);
            return parsed;
        });
        DumStructuredRecord good = DumStructuredRecord.builder("1", SCHEMA)
                .transientValue("salaryText", "R$ 3.000 a 5.000 por mes").build();
        DumStructuredRecord bad = DumStructuredRecord.builder("2", SCHEMA)
                .transientValue("salaryText", "R$ 3.000 a ?").build();

        DumStructuredRecord split = DumEnrichmentChain.of(salary).apply(null, good);
        DumStructuredRecord partial = DumEnrichmentChain.of(salary).apply(null, bad);

        assertEquals(3000.0, split.values().get("salaryMin"));
        assertEquals(5000.0, split.values().get("salaryMax"));
        assertEquals("month", split.values().get("salaryPeriod"));
        assertEquals("salaryText", split.provenance().get("salaryMin").selector());
        assertEquals(3000.0, partial.values().get("salaryMin"), "D102: the good fields still land");
        assertFalse(partial.has("salaryMax"));
    }

    @Test
    void theShapesRefuseAnIncompleteDeclaration() {
        assertThrows(IllegalStateException.class,
                () -> DumFallbackEnricher.builder("c", "contract").parser(t -> Optional.empty()).build());
        assertThrows(IllegalStateException.class,
                () -> DumFallbackEnricher.builder("c", "contract").input("title").build());
        assertThrows(IllegalArgumentException.class, () -> DumSplitEnricher.of("s", "", t -> Map.of()));
    }

    private static Optional<?> contract(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\bpj\\b.*")) {
            return Optional.of("PJ");
        }
        if (lower.matches(".*\\bclt\\b.*")) {
            return Optional.of("CLT");
        }
        return Optional.empty();
    }
}
