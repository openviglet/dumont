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
 * An {@link DumEnricher} that fills one multi-valued field with the values an LLM lists from a
 * record's text (roadmap <strong>D131</strong>): the skills a job description requires, the
 * products a page names. The open-vocabulary sibling of {@link DumClassificationEnricher}, and
 * under the same rules.
 *
 * <ul>
 * <li><strong>Grounded.</strong> Only an item that occurs in the input is published, spelled as
 * the input spells it ({@link DumExtractor#grounded}); an item the model invented is dropped. No
 * item grounded means the field is omitted.</li>
 * <li><strong>Config.</strong> Disabled, it never calls the extractor.</li>
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
public final class DumExtractionEnricher implements DumEnricher {

    /** How a cached list is joined: no grounded item can hold a line break, folded to a space. */
    private static final String SEPARATOR = "\n";
    private static final int UNCAPPED = -1;

    private final String name;
    private final boolean enabled;
    private final String targetField;
    private final Function<DumStructuredRecord, String> inputBuilder;
    private final DumExtractor extractor;
    private final DumClassificationStore store;
    private final boolean overwriteSourced;
    private final int maxItems;
    private final int dailyCap;
    private final Clock clock;

    private DumExtractionEnricher(Builder builder) {
        this.name = builder.name;
        this.enabled = builder.enabled;
        this.targetField = builder.targetField;
        this.inputBuilder = builder.inputBuilder;
        this.extractor = builder.extractor;
        this.store = builder.store != null ? builder.store : new DumEnrichmentCache(0);
        this.overwriteSourced = builder.overwriteSourced;
        this.maxItems = builder.maxItems;
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
        List<String> items = items(input);
        return items.isEmpty() ? Map.of() : Map.of(targetField, items);
    }

    /** The stored items for {@code input}, else the extractor's when the cap grants a call. */
    private List<String> items(String input) {
        String key = DumClassificationStore.hash(name + '\n' + input);
        Optional<DumClassificationStore.Answer> cached = store.find(key);
        if (cached.isPresent()) {
            return cached.get().asOptional().map(joined -> List.of(joined.split(SEPARATOR)))
                    .orElse(List.of());
        }
        if (!acquire()) {
            log.debug("Extractor '{}' reached its daily cap of {}: field '{}' omitted, not cached",
                    name, dailyCap, targetField);
            return List.of();
        }
        List<String> items;
        try {
            items = DumExtractor.grounded(extractor.extract(input), input);
        } catch (RuntimeException e) {
            log.warn("Extractor '{}' failed — field '{}' will be omitted for this record: {}",
                    name, targetField, e.getMessage());
            return List.of();
        }
        if (items.size() > maxItems) {
            items = items.subList(0, maxItems);
        }
        store.put(key, new DumClassificationStore.Answer(
                items.isEmpty() ? null : String.join(SEPARATOR, items), clock.instant()));
        return items;
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
        private Function<DumStructuredRecord, String> inputBuilder;
        private DumExtractor extractor;
        private DumClassificationStore store;
        private boolean overwriteSourced;
        private int maxItems = Integer.MAX_VALUE;
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

        /** The multi-valued schema field the grounded items are written to. */
        public Builder targetField(String targetField) {
            this.targetField = targetField;
            return this;
        }

        /** Builds the text handed to the extractor from a record. */
        public Builder input(Function<DumStructuredRecord, String> inputBuilder) {
            this.inputBuilder = inputBuilder;
            return this;
        }

        /** Reads the given fields, joined by a blank line, as the extractor input. */
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

        public Builder extractor(DumExtractor extractor) {
            this.extractor = extractor;
            return this;
        }

        /** Where answers and calls are kept; the default is an in-memory {@link DumEnrichmentCache}. */
        public Builder store(DumClassificationStore store) {
            this.store = store;
            return this;
        }

        /** At most this many items, the first grounded ones; unset, all of them. */
        public Builder maxItems(int maxItems) {
            if (maxItems < 1) {
                throw new IllegalArgumentException("maxItems must be one or more");
            }
            this.maxItems = maxItems;
            return this;
        }

        /** At most this many extractor calls in any rolling 24 hours; {@code 0} allows none. */
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

        /** When {@code true}, extract even if the source already declared the field (default false). */
        public Builder overwriteSourced(boolean overwriteSourced) {
            this.overwriteSourced = overwriteSourced;
            return this;
        }

        public DumExtractionEnricher build() {
            if (targetField == null || targetField.isBlank()) {
                throw new IllegalArgumentException("targetField is required");
            }
            if (inputBuilder == null) {
                throw new IllegalArgumentException("input or inputFields is required");
            }
            if (extractor == null) {
                throw new IllegalArgumentException("extractor is required");
            }
            return new DumExtractionEnricher(this);
        }
    }
}
