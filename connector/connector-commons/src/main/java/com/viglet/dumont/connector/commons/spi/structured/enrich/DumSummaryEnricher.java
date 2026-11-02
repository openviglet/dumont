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

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

import lombok.extern.slf4j.Slf4j;

/**
 * An {@link DumEnricher} that fills one text field with a short summary an LLM writes from a
 * record's text (roadmap <strong>D131</strong>): a job's two-line abstract. The prose sibling of
 * {@link DumExtractionEnricher}, and under the same rules.
 *
 * <ul>
 * <li><strong>Grounded.</strong> Only a summary {@link DumSummarizer#grounded} accepts is
 * published; one naming a number or a name the input does not hold is dropped, and the field is
 * omitted.</li>
 * <li><strong>Marked.</strong> A published summary always carries {@link Builder#generatedMarker}
 * beside it, so a reader can tell generated prose from the source's own.</li>
 * <li><strong>Config.</strong> Disabled, it never calls the summarizer.</li>
 * <li><strong>Cache.</strong> Answers are kept in a {@link DumClassificationStore} under the SHA-256
 * of the enricher's name and the input, an empty answer included. A failed call and a call the cap
 * refused are not cached.</li>
 * <li><strong>Cap.</strong> With {@link Builder#dailyCap}, at most that many calls in any rolling 24
 * hours, counted in the store.</li>
 * <li><strong>Sourced data wins.</strong> A field the source already declared is left alone unless
 * {@link Builder#overwriteSourced} says otherwise.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumSummaryEnricher implements DumEnricher {

    private static final int UNCAPPED = -1;

    private final String name;
    private final boolean enabled;
    private final String targetField;
    private final String markerField;
    private final String markerValue;
    private final Function<DumStructuredRecord, String> inputBuilder;
    private final DumSummarizer summarizer;
    private final DumClassificationStore store;
    private final boolean overwriteSourced;
    private final int maxChars;
    private final double minGroundedShare;
    private final int dailyCap;
    private final Clock clock;

    private DumSummaryEnricher(Builder builder) {
        this.name = builder.name;
        this.enabled = builder.enabled;
        this.targetField = builder.targetField;
        this.markerField = builder.markerField;
        this.markerValue = builder.markerValue;
        this.inputBuilder = builder.inputBuilder;
        this.summarizer = builder.summarizer;
        this.store = builder.store != null ? builder.store : new DumEnrichmentCache(0);
        this.overwriteSourced = builder.overwriteSourced;
        this.maxChars = builder.maxChars;
        this.minGroundedShare = builder.minGroundedShare;
        this.dailyCap = builder.dailyCap;
        this.clock = builder.clock;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        if (!enabled || (!overwriteSourced && rec.has(targetField))) {
            return Map.of();
        }
        String input = inputBuilder.apply(rec);
        if (input == null || input.isBlank()) {
            return Map.of();
        }
        return summary(input).<Map<String, Object>>map(text -> Map.of(targetField, text, markerField, markerValue))
                .orElse(Map.of());
    }

    /** The stored summary for {@code input}, else the summarizer's when the cap grants a call. */
    private Optional<String> summary(String input) {
        String key = DumClassificationStore.hash(name + '\n' + input);
        Optional<DumClassificationStore.Answer> cached = store.find(key);
        if (cached.isPresent()) {
            return cached.get().asOptional();
        }
        if (!acquire()) {
            log.debug("Summarizer '{}' reached its daily cap of {}: field '{}' omitted, not cached",
                    name, dailyCap, targetField);
            return Optional.empty();
        }
        Optional<String> text;
        try {
            text = DumSummarizer.grounded(summarizer.summarize(input), input, maxChars, minGroundedShare);
        } catch (RuntimeException e) {
            log.warn("Summarizer '{}' failed — field '{}' will be omitted for this record: {}",
                    name, targetField, e.getMessage());
            return Optional.empty();
        }
        store.put(key, new DumClassificationStore.Answer(text.orElse(null), clock.instant()));
        return text;
    }

    /** Whether the cap grants one more call now, recording the call when it does. */
    private synchronized boolean acquire() {
        if (dailyCap == UNCAPPED) {
            return true;
        }
        Instant now = clock.instant();
        if (store.callsSince(name, now.minus(DumClassificationEnricher.CAP_WINDOW)) >= dailyCap) {
            return false;
        }
        store.recordCall(name, now);
        return true;
    }

    public static final class Builder {
        private final String name;
        private boolean enabled = true;
        private String targetField;
        private String markerField;
        private String markerValue = "generated";
        private Function<DumStructuredRecord, String> inputBuilder;
        private DumSummarizer summarizer;
        private DumClassificationStore store;
        private boolean overwriteSourced;
        private int maxChars;
        private double minGroundedShare = DumSummarizer.DEFAULT_MIN_GROUNDED_SHARE;
        private int dailyCap = UNCAPPED;
        private Clock clock = Clock.systemUTC();

        private Builder(String name) {
            this.name = name;
        }

        /** Whether the enricher runs at all; wire from {@code dumont.enrichment.llm.enabled}. */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /** The text schema field the grounded summary is written to, e.g. {@code abstract}. */
        public Builder targetField(String targetField) {
            this.targetField = targetField;
            return this;
        }

        /**
         * The schema field set beside a published summary, saying it was generated: {@code value}
         * is written to {@code field} whenever {@link #targetField} is. Required, since generated
         * prose must never read as the source's own.
         */
        public Builder generatedMarker(String field, String value) {
            this.markerField = field;
            this.markerValue = value;
            return this;
        }

        /** The marker field, with the value {@code generated}. */
        public Builder generatedMarker(String field) {
            return generatedMarker(field, "generated");
        }

        /** Builds the text handed to the summarizer from a record. */
        public Builder input(Function<DumStructuredRecord, String> inputBuilder) {
            this.inputBuilder = inputBuilder;
            return this;
        }

        /** Reads the given fields, joined by a blank line, as the summarizer input. */
        public Builder inputFields(String... fields) {
            List<String> names = Arrays.asList(fields);
            this.inputBuilder = rec -> {
                StringBuilder text = new StringBuilder();
                for (String field : names) {
                    Object value = rec.values().get(field);
                    if (value != null) {
                        text.append(text.isEmpty() ? "" : "\n\n").append(value);
                    }
                }
                return text.toString();
            };
            return this;
        }

        public Builder summarizer(DumSummarizer summarizer) {
            this.summarizer = summarizer;
            return this;
        }

        /** Where answers and calls are kept; the default is an in-memory {@link DumEnrichmentCache}. */
        public Builder store(DumClassificationStore store) {
            this.store = store;
            return this;
        }

        /** The length a summary must fit; a longer one is cut to whole sentences. Required. */
        public Builder maxChars(int maxChars) {
            if (maxChars < 1) {
                throw new IllegalArgumentException("maxChars must be one or more");
            }
            this.maxChars = maxChars;
            return this;
        }

        /** The share of the summary's other words that must occur in the input, 0 to 1. */
        public Builder minGroundedShare(double minGroundedShare) {
            if (minGroundedShare < 0 || minGroundedShare > 1) {
                throw new IllegalArgumentException("minGroundedShare must be between 0 and 1");
            }
            this.minGroundedShare = minGroundedShare;
            return this;
        }

        /** At most this many summarizer calls in any rolling 24 hours; {@code 0} allows none. */
        public Builder dailyCap(int dailyCap) {
            if (dailyCap < 0) {
                throw new IllegalArgumentException("dailyCap must be zero or more");
            }
            this.dailyCap = dailyCap;
            return this;
        }

        /** The clock answers are dated and calls counted by; injectable for tests. */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /** When {@code true}, summarize even if the source already declared the field (default false). */
        public Builder overwriteSourced(boolean overwriteSourced) {
            this.overwriteSourced = overwriteSourced;
            return this;
        }

        public DumSummaryEnricher build() {
            if (targetField == null || targetField.isBlank()) {
                throw new IllegalArgumentException("targetField is required");
            }
            if (markerField == null || markerField.isBlank() || markerField.equals(targetField)) {
                throw new IllegalArgumentException("generatedMarker is required, on a field of its own");
            }
            if (markerValue == null || markerValue.isBlank()) {
                throw new IllegalArgumentException("generatedMarker needs a value");
            }
            if (maxChars < 1) {
                throw new IllegalArgumentException("maxChars is required");
            }
            if (inputBuilder == null) {
                throw new IllegalArgumentException("input or inputFields is required");
            }
            if (summarizer == null) {
                throw new IllegalArgumentException("summarizer is required");
            }
            return new DumSummaryEnricher(this);
        }
    }
}
