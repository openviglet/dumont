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

import java.util.Map;
import java.util.Optional;
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
 * <li><strong>Cache.</strong> Inputs are de-duplicated through a {@link DumEnrichmentCache}; an
 * identical description is classified once per TTL, and a "no label" outcome is cached too.</li>
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

    private final String name;
    private final boolean enabled;
    private final String targetField;
    private final Function<DumStructuredRecord, String> inputBuilder;
    private final DumClassifier classifier;
    private final DumEnrichmentCache cache;
    private final boolean overwriteSourced;

    private DumClassificationEnricher(Builder builder) {
        this.name = builder.name;
        this.enabled = builder.enabled;
        this.targetField = builder.targetField;
        this.inputBuilder = builder.inputBuilder;
        this.classifier = builder.classifier;
        this.cache = builder.cache != null ? builder.cache : new DumEnrichmentCache(0);
        this.overwriteSourced = builder.overwriteSourced;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord record) {
        if (!enabled) {
            return Map.of();
        }
        if (!overwriteSourced && record.has(targetField)) {
            return Map.of();
        }
        String input = inputBuilder.apply(record);
        if (input == null || input.isBlank()) {
            return Map.of();
        }
        Optional<String> label = cache.resolve(input, () -> safeClassify(input));
        return label.filter(value -> !value.isBlank())
                .<Map<String, Object>>map(value -> Map.of(targetField, value))
                .orElseGet(Map::of);
    }

    private Optional<String> safeClassify(String input) {
        try {
            return classifier.classify(input);
        } catch (RuntimeException e) {
            log.warn("Classifier '{}' failed — field '{}' will be omitted for this record: {}",
                    name, targetField, e.getMessage());
            return Optional.empty();
        }
    }

    public static final class Builder {
        private final String name;
        private boolean enabled = true;
        private String targetField;
        private Function<DumStructuredRecord, String> inputBuilder;
        private DumClassifier classifier;
        private DumEnrichmentCache cache;
        private boolean overwriteSourced;

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
            this.inputBuilder = record -> {
                Object value = record.values().get(field);
                return value == null ? null : String.valueOf(value);
            };
            return this;
        }

        public Builder classifier(DumClassifier classifier) {
            this.classifier = classifier;
            return this;
        }

        public Builder cache(DumEnrichmentCache cache) {
            this.cache = cache;
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
            return new DumClassificationEnricher(this);
        }
    }
}
