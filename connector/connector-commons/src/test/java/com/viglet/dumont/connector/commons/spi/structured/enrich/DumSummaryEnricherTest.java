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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;

/** A generated summary publishes only when grounded, and always marked (roadmap D131). */
class DumSummaryEnricherTest {

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .text("description").text("abstract").string("abstract_source").build();
    private static final String TEXT = "Acme Logistics is hiring a backend developer in Campinas. "
            + "The developer will maintain Java services and design Kafka pipelines. "
            + "Salary of 9000 per month, hybrid work three days a week.";
    private static final String GOOD = "Acme Logistics is hiring a backend developer in Campinas "
            + "to maintain Java services and Kafka pipelines.";

    private static DumStructuredRecord job(String description) {
        return DumStructuredRecord.builder("job-1", SCHEMA).set("description", description).build();
    }

    private static DumSummaryEnricher.Builder enricher(DumSummarizer summarizer) {
        return DumSummaryEnricher.builder("abstract").targetField("abstract").generatedMarker("abstract_source")
                .inputFields("description").maxChars(200).summarizer(summarizer);
    }

    @Test
    void aGroundedSummaryIsPublishedWithItsMarker() {
        assertEquals(Map.of("abstract", GOOD, "abstract_source", "generated"),
                enricher(input -> "  " + GOOD.replace(" ", "\n ") + " ").build().enrich(null, job(TEXT)));
    }

    @Test
    void anInventedNumberOrNameDropsTheWholeSummary() {
        assertEquals(Optional.empty(), DumSummarizer.grounded(
                "Acme Logistics pays 12000 per month for a backend developer.", TEXT, 200, 0.6));
        assertEquals(Optional.empty(), DumSummarizer.grounded(
                "Acme Logistics is hiring a backend developer in Recife.", TEXT, 200, 0.6));
    }

    @Test
    void aSummaryMostlyInWordsTheTextNeverUsesIsDropped() {
        assertEquals(Optional.empty(), DumSummarizer.grounded(
                "An exciting opportunity with unbeatable perks, amazing culture and rapid growth.", TEXT, 200, 0.6));
    }

    @Test
    void inflectedWordsStillGround() {
        assertTrue(DumSummarizer.grounded("Maintaining services, designing pipelines.", TEXT, 200, 1.0).isPresent());
    }

    @Test
    void aLongSummaryIsCutToWholeSentencesOrDropped() {
        String two = GOOD + " Hybrid work three days a week.";
        assertEquals(Optional.of(GOOD), DumSummarizer.grounded(two, TEXT, GOOD.length() + 5, 0.6));
        assertEquals(Optional.empty(), DumSummarizer.grounded(GOOD, TEXT, 40, 0.6));
    }

    @Test
    void anAnswerIsCachedButAFailureIsNot() {
        AtomicInteger calls = new AtomicInteger();
        DumSummaryEnricher cached = enricher(input -> {
            calls.incrementAndGet();
            return "An exciting opportunity with unbeatable perks.";
        }).build();
        assertTrue(cached.enrich(null, job(TEXT)).isEmpty());
        assertTrue(cached.enrich(null, job(TEXT)).isEmpty());
        assertEquals(1, calls.get());

        AtomicInteger failing = new AtomicInteger();
        DumSummaryEnricher flaky = enricher(input -> {
            failing.incrementAndGet();
            throw new IllegalStateException("Turing unreachable");
        }).build();
        assertTrue(flaky.enrich(null, job(TEXT)).isEmpty());
        assertTrue(flaky.enrich(null, job(TEXT)).isEmpty());
        assertEquals(2, failing.get());
    }

    @Test
    void theCapHolds() {
        AtomicInteger calls = new AtomicInteger();
        DumSummaryEnricher capped = enricher(input -> {
            calls.incrementAndGet();
            return GOOD;
        }).dailyCap(1).build();

        assertEquals(GOOD, capped.enrich(null, job(TEXT)).get("abstract"));
        assertTrue(capped.enrich(null, job(TEXT + " Remote on Fridays.")).isEmpty());
        assertEquals(1, calls.get());
    }

    @Test
    void aSourcedFieldWinsAndADisabledEnricherNeverCalls() {
        AtomicInteger calls = new AtomicInteger();
        DumSummarizer counting = input -> {
            calls.incrementAndGet();
            return GOOD;
        };
        DumStructuredRecord sourced = DumStructuredRecord.builder("job-1", SCHEMA).set("description", TEXT)
                .set("abstract", "The source's own abstract.").build();

        assertTrue(enricher(counting).build().enrich(null, sourced).isEmpty());
        assertTrue(enricher(counting).enabled(false).build().enrich(null, job(TEXT)).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void theMarkerAndTheCapAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> DumSummaryEnricher.builder("abstract")
                .targetField("abstract").inputFields("description").maxChars(200).summarizer(input -> GOOD).build());
        assertThrows(IllegalArgumentException.class, () -> DumSummaryEnricher.builder("abstract")
                .targetField("abstract").generatedMarker("abstract").inputFields("description").maxChars(200)
                .summarizer(input -> GOOD).build());
        assertThrows(IllegalArgumentException.class, () -> DumSummaryEnricher.builder("abstract")
                .targetField("abstract").generatedMarker("abstract_source").inputFields("description")
                .summarizer(input -> GOOD).build());
    }
}
