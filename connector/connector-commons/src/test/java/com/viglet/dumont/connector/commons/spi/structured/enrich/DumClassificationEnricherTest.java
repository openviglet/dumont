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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;

class DumClassificationEnricherTest {

    private final DumStructuredSchema schema = DumStructuredSchema.builder()
            .text("description")
            .facet("area")
            .build();

    private DumStructuredRecord record(String description) {
        DumStructuredRecord.Builder builder = DumStructuredRecord.builder("r", schema);
        if (description != null) {
            builder.set("description", description);
        }
        return builder.build();
    }

    private DumClassificationEnricher.Builder base(DumClassifier classifier) {
        return DumClassificationEnricher.builder("area-llm")
                .targetField("area")
                .inputField("description")
                .classifier(classifier)
                .cache(new DumEnrichmentCache(0));
    }

    @Test
    void disabledIsANoOpAndNeverCallsTheClassifier() {
        AtomicInteger calls = new AtomicInteger();
        DumClassificationEnricher enricher = base(input -> {
            calls.incrementAndGet();
            return Optional.of("X");
        }).enabled(false).build();

        assertTrue(enricher.enrich(null, record("anything")).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void classifiesWhenEnabled() {
        DumClassificationEnricher enricher =
                base(input -> Optional.of("Exact Sciences")).build();
        assertEquals(Map.of("area", "Exact Sciences"),
                enricher.enrich(null, record("intro calculus")));
    }

    @Test
    void emptyClassificationOmitsTheField() {
        DumClassificationEnricher enricher = base(input -> Optional.empty()).build();
        assertTrue(enricher.enrich(null, record("ambiguous")).isEmpty(),
                "no confident label -> field omitted, never fabricated");
    }

    @Test
    void aThrowingClassifierDegradesToOmission() {
        DumClassificationEnricher enricher = base(input -> {
            throw new RuntimeException("LLM down");
        }).build();
        assertTrue(enricher.enrich(null, record("x")).isEmpty(),
                "a failed call omits the field rather than failing the record");
    }

    @Test
    void doesNotOverrideASourcedValueByDefault() {
        AtomicInteger calls = new AtomicInteger();
        DumClassificationEnricher enricher = base(input -> {
            calls.incrementAndGet();
            return Optional.of("Model Guess");
        }).build();

        DumStructuredRecord sourced = DumStructuredRecord.builder("r", schema)
                .set("description", "history survey")
                .set("area", "Humanities")
                .build();

        assertTrue(enricher.enrich(null, sourced).isEmpty(),
                "sourced data wins; the classifier is not even consulted");
        assertEquals(0, calls.get());
    }

    @Test
    void blankInputContributesNothing() {
        AtomicInteger calls = new AtomicInteger();
        DumClassificationEnricher enricher = base(input -> {
            calls.incrementAndGet();
            return Optional.of("X");
        }).build();
        assertTrue(enricher.enrich(null, record(null)).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void identicalInputIsClassifiedOncePerCache() {
        AtomicInteger calls = new AtomicInteger();
        DumEnrichmentCache cache = new DumEnrichmentCache(0);
        DumClassifier classifier = input -> {
            calls.incrementAndGet();
            return Optional.of("Exact Sciences");
        };
        DumClassificationEnricher enricher = DumClassificationEnricher.builder("area-llm")
                .targetField("area").inputField("description").classifier(classifier).cache(cache)
                .build();

        enricher.enrich(null, record("intro calculus"));
        Map<String, Object> second = enricher.enrich(null, record("intro calculus"));

        assertEquals(Map.of("area", "Exact Sciences"), second);
        assertEquals(1, calls.get(), "the cache spares the second identical call");
    }
}
