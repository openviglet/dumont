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
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

import lombok.extern.slf4j.Slf4j;

/**
 * An {@link DumEnricher} that fills one field with an LLM-classified label (roadmap
 * <strong>D04</strong>): the {@code llm_area} shape — assign a canonical category to a record whose
 * description is too thin or whose raw classification is ambiguous.
 *
 * <p>
 * The LLM path is <strong>behind config + cache</strong>, exactly as the roadmap requires:
 * <ul>
 * <li><strong>Config.</strong> When {@code enabled} is {@code false} the enricher is an immediate
 * no-op — the classifier is never called. The flag is wired from {@code dumont.enrichment.llm.*}
 * configuration in {@code connector-app}.</li>
 * <li><strong>Cache.</strong> Inputs are de-duplicated through a {@link DumClassificationStore}, keyed
 * by the SHA-256 of the enricher's name, its label set and the input (D107). An identical input is
 * classified once, and a "no label" answer is cached too. A failed call (the classifier threw) and a
 * call the cap refused are <em>not</em> cached, so they are asked again later rather than read as
 * "no label". The default store is an in-memory {@link DumEnrichmentCache}; the persistent one
 * {@code connector-app} registers survives a restart.</li>
 * <li><strong>Cap.</strong> With {@link Builder#dailyCap}, at most that many calls are made in any
 * rolling 24 hours, counted in the store so the cap holds across runs (D107).</li>
 * <li><strong>Closed label set.</strong> With {@link Builder#labels}, the classifier is asked through
 * {@link DumClassifier#classify(String, Collection)} and an answer outside the set is "no label"
 * (D107).</li>
 * </ul>
 *
 * <p>
 * <strong>Grounding contract.</strong> By default the enricher only fills the target field when the
 * <em>source did not declare it</em> ({@code overwriteSourced=false}) — sourced data always wins over
 * a model guess. When the classifier returns {@link Optional#empty()} (unsure, or the call failed)
 * the field is omitted, never fabricated. The model supplies a <em>classification</em>, never a
 * sourced attribute value.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public final class DumClassificationEnricher implements DumEnricher {

    /** The window {@link Builder#dailyCap} counts calls over. */
    public static final Duration CAP_WINDOW = Duration.ofDays(1);

    private static final int UNCAPPED = -1;

    private final String name;
    private final boolean enabled;
    private final String targetField;
    private final Function<DumStructuredRecord, String> inputBuilder;
    private final DumClassifier classifier;
    private final DumClassificationStore store;
    private final boolean overwriteSourced;
    private final List<String> labels;
    private final String keyPrefix;
    private final int dailyCap;
    private final Clock clock;

    private DumClassificationEnricher(Builder builder) {
        this.name = builder.name;
        this.enabled = builder.enabled;
        this.targetField = builder.targetField;
        this.inputBuilder = builder.inputBuilder;
        this.classifier = builder.classifier;
        this.store = builder.store != null ? builder.store : new DumEnrichmentCache(0);
        this.overwriteSourced = builder.overwriteSourced;
        this.labels = builder.labels;
        this.keyPrefix = name + '\n'
                + (labels == null ? "" : String.join("\n", new TreeSet<>(labels))) + '\n';
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
        if (!enabled) {
            return Map.of();
        }
        if (!overwriteSourced && rec.has(targetField)) {
            return Map.of();
        }
        String input = inputBuilder.apply(rec);
        if (input == null || input.isBlank()) {
            return Map.of();
        }
        return label(input).filter(value -> !value.isBlank())
                .<Map<String, Object>>map(value -> Map.of(targetField, value))
                .orElseGet(Map::of);
    }

    /** The stored answer for {@code input}, else the classifier's when the cap grants a call. */
    private Optional<String> label(String input) {
        String key = DumClassificationStore.hash(keyPrefix + input);
        Optional<DumClassificationStore.Answer> cached = store.find(key);
        if (cached.isPresent()) {
            return cached.get().asOptional().flatMap(this::checked);
        }
        if (!acquire()) {
            log.debug("Classifier '{}' reached its daily cap of {}: field '{}' omitted, not cached",
                    name, dailyCap, targetField);
            return Optional.empty();
        }
        Optional<String> label;
        try {
            label = (labels == null ? classifier.classify(input) : classifier.classify(input, labels))
                    .flatMap(this::checked);
        } catch (RuntimeException e) {
            log.warn("Classifier '{}' failed — field '{}' will be omitted for this record: {}",
                    name, targetField, e.getMessage());
            return Optional.empty();
        }
        store.put(key, new DumClassificationStore.Answer(label.orElse(null), clock.instant()));
        return label;
    }

    /** {@code label} itself, or its spelling in the label set; empty when the set does not hold it. */
    private Optional<String> checked(String label) {
        return labels == null ? Optional.of(label) : DumClassifier.match(label, labels);
    }

    /** Whether the cap grants one more call now, recording the call when it does. */
    private synchronized boolean acquire() {
        if (dailyCap == UNCAPPED) {
            return true;
        }
        Instant now = clock.instant();
        if (store.callsSince(name, now.minus(CAP_WINDOW)) >= dailyCap) {
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
        private DumClassifier classifier;
        private DumClassificationStore store;
        private boolean overwriteSourced;
        private List<String> labels;
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

        /** The schema field the classified label is written to. */
        public Builder targetField(String targetField) {
            this.targetField = targetField;
            return this;
        }

        /** Builds the text handed to the classifier from a record (e.g. its description). */
        public Builder input(Function<DumStructuredRecord, String> inputBuilder) {
            this.inputBuilder = inputBuilder;
            return this;
        }

        /** Reads a single field as the classifier input — the common case. */
        public Builder inputField(String field) {
            this.inputBuilder = rec -> {
                Object value = rec.values().get(field);
                return value == null ? null : String.valueOf(value);
            };
            return this;
        }

        public Builder classifier(DumClassifier classifier) {
            this.classifier = classifier;
            return this;
        }

        /** An in-memory cache: the same as {@link #store} given a {@link DumEnrichmentCache}. */
        public Builder cache(DumEnrichmentCache cache) {
            this.store = cache;
            return this;
        }

        /** Where answers and calls are kept; the default is an in-memory {@link DumEnrichmentCache}. */
        public Builder store(DumClassificationStore store) {
            this.store = store;
            return this;
        }

        /**
         * The closed label set: only one of these is ever published, and a change to the set makes
         * every cached answer a miss. Unset, any label the classifier returns is published.
         */
        public Builder labels(Collection<String> labels) {
            this.labels = labels == null ? null : List.copyOf(labels);
            return this;
        }

        /** At most this many classifier calls in any rolling 24 hours; {@code 0} allows none. */
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

        /** When {@code true}, classify even if the source already declared the field (default false). */
        public Builder overwriteSourced(boolean overwriteSourced) {
            this.overwriteSourced = overwriteSourced;
            return this;
        }

        public DumClassificationEnricher build() {
            if (targetField == null || targetField.isBlank()) {
                throw new IllegalArgumentException("targetField is required");
            }
            if (inputBuilder == null) {
                throw new IllegalArgumentException("input or inputField is required");
            }
            if (classifier == null) {
                throw new IllegalArgumentException("classifier is required");
            }
            if (labels != null && labels.isEmpty()) {
                throw new IllegalArgumentException("labels, when given, must not be empty");
            }
            return new DumClassificationEnricher(this);
        }
    }
}
