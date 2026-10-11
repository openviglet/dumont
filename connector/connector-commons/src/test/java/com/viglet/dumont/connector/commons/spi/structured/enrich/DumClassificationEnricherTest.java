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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    private DumStructuredRecord rec(String description) {
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

        assertTrue(enricher.enrich(null, rec("anything")).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void classifiesWhenEnabled() {
        DumClassificationEnricher enricher =
                base(input -> Optional.of("Exact Sciences")).build();
        assertEquals(Map.of("area", "Exact Sciences"),
                enricher.enrich(null, rec("intro calculus")));
    }

    @Test
    void emptyClassificationOmitsTheField() {
        DumClassificationEnricher enricher = base(input -> Optional.empty()).build();
        assertTrue(enricher.enrich(null, rec("ambiguous")).isEmpty(),
                "no confident label -> field omitted, never fabricated");
    }

    @Test
    void aThrowingClassifierDegradesToOmission() {
        DumClassificationEnricher enricher = base(input -> {
            throw new RuntimeException("LLM down");
        }).build();
        assertTrue(enricher.enrich(null, rec("x")).isEmpty(),
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
        assertTrue(enricher.enrich(null, rec(null)).isEmpty());
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

        enricher.enrich(null, rec("intro calculus"));
        Map<String, Object> second = enricher.enrich(null, rec("intro calculus"));

        assertEquals(Map.of("area", "Exact Sciences"), second);
        assertEquals(1, calls.get(), "the cache spares the second identical call");
    }

    @Test
    void aFailedCallIsNotCachedAsNoLabel() {
        AtomicInteger calls = new AtomicInteger();
        DumClassificationEnricher enricher = base(input -> {
            if (calls.incrementAndGet() == 1) {
                throw new RuntimeException("LLM down");
            }
            return Optional.of("Exact Sciences");
        }).build();

        assertTrue(enricher.enrich(null, rec("intro calculus")).isEmpty());
        assertEquals(Map.of("area", "Exact Sciences"), enricher.enrich(null, rec("intro calculus")),
                "the failure was not remembered, so the input is asked again");
        assertEquals(2, calls.get());
    }

    @Test
    void theDailyCapRefusesCallsWithoutCachingThem() {
        AtomicInteger calls = new AtomicInteger();
        MutableClock clock = new MutableClock(Instant.parse("2026-10-11T10:00:00Z"));
        DumEnrichmentCache store = new DumEnrichmentCache(0);
        DumClassificationEnricher enricher = DumClassificationEnricher.builder("area-llm")
                .targetField("area").inputField("description").store(store).clock(clock)
                .classifier(input -> {
                    calls.incrementAndGet();
                    return Optional.of("Exact Sciences");
                }).dailyCap(2).build();

        enricher.enrich(null, rec("one"));
        enricher.enrich(null, rec("two"));
        assertTrue(enricher.enrich(null, rec("three")).isEmpty(), "the third call is refused");
        assertEquals(2, calls.get());

        clock.advance(Duration.ofHours(25));
        assertEquals(Map.of("area", "Exact Sciences"), enricher.enrich(null, rec("three")),
                "a refused input is not cached as no label, so it is asked once the window rolls");
        assertEquals(3, calls.get());
    }

    @Test
    void theCapIsCountedInTheStoreSoItHoldsAcrossEnrichers() {
        DumEnrichmentCache store = new DumEnrichmentCache(0);
        DumClassifier classifier = input -> Optional.of("Exact Sciences");
        DumClassificationEnricher first = DumClassificationEnricher.builder("area-llm")
                .targetField("area").inputField("description").store(store).classifier(classifier)
                .dailyCap(1).build();
        DumClassificationEnricher restarted = DumClassificationEnricher.builder("area-llm")
                .targetField("area").inputField("description").store(store).classifier(classifier)
                .dailyCap(1).build();

        first.enrich(null, rec("one"));
        assertTrue(restarted.enrich(null, rec("two")).isEmpty(), "the store already holds today's call");
        assertEquals(Map.of("area", "Exact Sciences"), restarted.enrich(null, rec("one")),
                "a cached answer costs no call");
    }

    @Test
    void aZeroCapNeverCallsTheClassifier() {
        AtomicInteger calls = new AtomicInteger();
        DumClassificationEnricher enricher = base(input -> {
            calls.incrementAndGet();
            return Optional.of("X");
        }).dailyCap(0).build();

        assertTrue(enricher.enrich(null, rec("anything")).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void anAnswerOutsideTheLabelSetIsNoLabel() {
        DumClassificationEnricher enricher = base(input -> Optional.of("Astrology"))
                .labels(List.of("Exact Sciences", "Humanities")).build();

        assertTrue(enricher.enrich(null, rec("horoscope")).isEmpty(),
                "a label the set does not hold is never published");
    }

    @Test
    void anAnswerIsPublishedInTheSetsOwnSpelling() {
        DumClassificationEnricher enricher = base(input -> Optional.of(" \"exact sciences.\" "))
                .labels(List.of("Exact Sciences", "Humanities")).build();

        assertEquals(Map.of("area", "Exact Sciences"), enricher.enrich(null, rec("intro calculus")));
    }

    @Test
    void theLabelSetReachesTheClassifier() {
        List<Collection<String>> seen = new ArrayList<>();
        DumClassifier classifier = new DumClassifier() {
            @Override
            public Optional<String> classify(String input) {
                throw new AssertionError("the closed-set call is the one expected");
            }

            @Override
            public Optional<String> classify(String input, Collection<String> labels) {
                seen.add(labels);
                return Optional.of("Humanities");
            }
        };
        DumClassificationEnricher enricher = base(classifier)
                .labels(List.of("Exact Sciences", "Humanities")).build();

        assertEquals(Map.of("area", "Humanities"), enricher.enrich(null, rec("history survey")));
        assertEquals(List.of(List.of("Exact Sciences", "Humanities")), seen);
    }

    @Test
    void aChangedLabelSetIsACacheMiss() {
        AtomicInteger calls = new AtomicInteger();
        DumEnrichmentCache store = new DumEnrichmentCache(0);
        DumClassifier classifier = input -> {
            calls.incrementAndGet();
            return Optional.of("Humanities");
        };
        DumClassificationEnricher v1 = base(classifier).store(store)
                .labels(List.of("Exact Sciences", "Humanities")).build();
        DumClassificationEnricher v2 = base(classifier).store(store)
                .labels(List.of("Exact Sciences", "Humanities", "Arts")).build();

        v1.enrich(null, rec("history survey"));
        v1.enrich(null, rec("history survey"));
        v2.enrich(null, rec("history survey"));
        assertEquals(2, calls.get(), "one call per label set, none for the repeat");
    }

    @Test
    void anEmptyLabelSetIsRefused() {
        DumClassificationEnricher.Builder builder = base(input -> Optional.of("X")).labels(List.of());
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void matchReadsOnlyALabelOfTheSet() {
        List<String> labels = List.of("Exact Sciences", "Humanities");
        assertEquals(Optional.of("Humanities"), DumClassifier.match("Humanities", labels));
        assertEquals(Optional.of("Humanities"), DumClassifier.match("'humanities'.", labels));
        assertTrue(DumClassifier.match("NONE", labels).isEmpty());
        assertTrue(DumClassifier.match("Humanities and Arts", labels).isEmpty());
        assertTrue(DumClassifier.match("  ", labels).isEmpty());
        assertTrue(DumClassifier.match(null, labels).isEmpty());
    }

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
