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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;

/** Grounded list extraction: only what the text holds is published (roadmap D131). */
class DumExtractionEnricherTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .text("description").facet("skills").build();
    private static final String TEXT = "We need a developer fluent in Java and Spring Boot.\n"
            + "JavaScript is a plus; experience with   Apache\nKafka too.";

    private static DumStructuredRecord job(String description) {
        return DumStructuredRecord.builder("job-1", SCHEMA).set("description", description).build();
    }

    private static DumExtractionEnricher.Builder enricher(DumExtractor extractor) {
        return DumExtractionEnricher.builder("skills").targetField("skills").inputFields("description")
                .extractor(extractor);
    }

    @Test
    void anInventedItemIsDroppedAndAGroundedOneKeepsTheTextsSpelling() {
        Map<String, Object> out = enricher(input -> List.of("java", "Python", "apache kafka", "Java"))
                .build().enrich(null, job(TEXT));

        assertEquals(Map.of("skills", List.of("Java", "Apache Kafka")), out);
    }

    @Test
    void anItemOnlyInsideAnotherWordIsNotGrounded() {
        assertEquals(List.of(), DumExtractor.grounded(List.of("Script"), TEXT));
        assertEquals(List.of("JavaScript"), DumExtractor.grounded(List.of("javascript"), TEXT));
    }

    @Test
    void nothingGroundedOmitsTheField() {
        assertTrue(enricher(input -> List.of("Rust")).build().enrich(null, job(TEXT)).isEmpty());
    }

    @Test
    void anAnswerIsCachedButAFailureIsNot() {
        AtomicInteger calls = new AtomicInteger();
        DumExtractionEnricher cached = enricher(input -> {
            calls.incrementAndGet();
            return List.of("Java");
        }).build();
        cached.enrich(null, job(TEXT));
        cached.enrich(null, job(TEXT));
        assertEquals(1, calls.get());

        AtomicInteger failing = new AtomicInteger();
        DumExtractionEnricher flaky = enricher(input -> {
            failing.incrementAndGet();
            throw new IllegalStateException("Turing unreachable");
        }).build();
        assertTrue(flaky.enrich(null, job(TEXT)).isEmpty());
        assertTrue(flaky.enrich(null, job(TEXT)).isEmpty());
        assertEquals(2, failing.get());
    }

    @Test
    void theCapAndTheItemLimitHold() {
        AtomicInteger calls = new AtomicInteger();
        DumExtractionEnricher capped = enricher(input -> {
            calls.incrementAndGet();
            return List.of("Java", "Spring Boot", "Kafka");
        }).dailyCap(1).maxItems(2).build();

        assertEquals(Map.of("skills", List.of("Java", "Spring Boot")), capped.enrich(null, job(TEXT)));
        assertTrue(capped.enrich(null, job("Another text naming Java.")).isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void aSourcedFieldWinsAndADisabledEnricherNeverCalls() {
        AtomicInteger calls = new AtomicInteger();
        DumExtractor counting = input -> {
            calls.incrementAndGet();
            return List.of("Java");
        };
        DumStructuredRecord sourced = DumStructuredRecord.builder("job-1", SCHEMA).set("description", TEXT)
                .set("skills", List.of("Cobol")).build();

        assertTrue(enricher(counting).build().enrich(null, sourced).isEmpty());
        assertTrue(enricher(counting).enabled(false).build().enrich(null, job(TEXT)).isEmpty());
        assertEquals(0, calls.get());
    }
}
