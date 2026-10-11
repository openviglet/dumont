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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * An ordered fallback over inputs (roadmap <strong>D103</strong>): "the partner's field, else the
 * title, else the text; the first input that decides wins". Each input is a field or a transient
 * attribute (D97) of the record. Its text goes through the parser, and the first non-empty answer
 * is written to the target with provenance naming the input it came from and that input's
 * confidence (D101). No input deciding means no contribution, never a default.
 *
 * <pre>{@code
 * DumFallbackEnricher.builder("contract", "contract")
 *         .input("contractType", 1.0).input("title", 0.8).input("description", 0.6)
 *         .parser(DumJobContracts::parse)
 *         .build();
 * }</pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumFallbackEnricher implements DumEnricher {

    private final String name;
    private final String targetField;
    private final List<Input> inputs;
    private final Function<String, Optional<?>> parser;
    private final Predicate<String> stop;

    private DumFallbackEnricher(Builder builder) {
        this.name = builder.name;
        this.targetField = builder.targetField;
        this.inputs = List.copyOf(builder.inputs);
        this.parser = builder.parser;
        this.stop = builder.stop;
    }

    public static Builder builder(String name, String targetField) {
        return new Builder(name, targetField);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        for (Input input : inputs) {
            for (String text : texts(rec, input.key())) {
                if (stop.test(text)) {
                    return Map.of();
                }
                Optional<?> decided = parser.apply(text);
                if (decided.isPresent() && !String.valueOf(decided.get()).isBlank()) {
                    return Map.of(targetField, DumSourced.of(decided.get(), input.key(), input.confidence()));
                }
            }
        }
        return Map.of();
    }

    /** The text values of a field, else of a transient attribute, in order. */
    static List<String> texts(DumStructuredRecord rec, String key) {
        Object value = rec.values().containsKey(key) ? rec.values().get(key) : rec.transients().get(key);
        List<String> texts = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            collection.stream().filter(v -> v != null).map(String::valueOf).filter(s -> !s.isBlank())
                    .forEach(texts::add);
        } else if (value != null && !String.valueOf(value).isBlank()) {
            texts.add(String.valueOf(value));
        }
        return texts;
    }

    private record Input(String key, Double confidence) {
    }

    public static final class Builder {
        private final String name;
        private final String targetField;
        private final List<Input> inputs = new ArrayList<>();
        private Function<String, Optional<?>> parser;
        private Predicate<String> stop = text -> false;

        private Builder(String name, String targetField) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("A fallback enricher needs a name");
            }
            if (targetField == null || targetField.isBlank()) {
                throw new IllegalArgumentException("A fallback enricher needs a target field");
            }
            this.name = name;
            this.targetField = targetField;
        }

        /** The next input to try: a field, else a transient attribute, of the record. */
        public Builder input(String key) {
            return input(key, null);
        }

        /** The next input to try, with the confidence a value read from it carries (0-1). */
        public Builder input(String key, Double confidence) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("A fallback input needs a key");
            }
            inputs.add(new Input(key, confidence));
            return this;
        }

        /** Decides a value from one input's text, or answers empty to pass to the next input. */
        public Builder parser(Function<String, Optional<?>> parser) {
            this.parser = parser;
            return this;
        }

        /**
         * An input whose text {@code stop} accepts ends the fallback with no contribution, instead of
         * passing to the next input (roadmap D117): a title naming two contract kinds settles that
         * the contract is unknown, and the description is not read to guess one.
         */
        public Builder stopOn(Predicate<String> stop) {
            this.stop = stop == null ? text -> false : stop;
            return this;
        }

        public DumFallbackEnricher build() {
            if (inputs.isEmpty()) {
                throw new IllegalStateException("Fallback enricher '" + name + "' has no inputs");
            }
            if (parser == null) {
                throw new IllegalStateException("Fallback enricher '" + name + "' has no parser");
            }
            return new DumFallbackEnricher(this);
        }
    }
}
