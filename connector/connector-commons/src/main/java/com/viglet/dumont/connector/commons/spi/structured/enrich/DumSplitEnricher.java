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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * One input producing several fields (roadmap <strong>D103</strong>): salary text to minimum,
 * maximum, period and band. The parser turns the input's text into field values, and each lands
 * with provenance naming the input (D101). A field the parser leaves out, or maps to
 * {@code null}, is not contributed. Because the chain drops an invalid contribution alone (D102),
 * one bad number cannot take the other fields with it.
 *
 * <pre>{@code
 * DumSplitEnricher.of("salary", "salaryText", DumSalaries::parse)
 * }</pre>
 *
 * <p>
 * The input is a field or a transient attribute (D97); for a multi-valued input, the first value
 * is parsed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumSplitEnricher implements DumEnricher {

    private final String name;
    private final String input;
    private final Function<String, Map<String, ?>> parser;

    private DumSplitEnricher(String name, String input, Function<String, Map<String, ?>> parser) {
        this.name = name;
        this.input = input;
        this.parser = parser;
    }

    /**
     * @throws IllegalArgumentException when the name, input or parser is missing
     */
    public static DumSplitEnricher of(String name, String input, Function<String, Map<String, ?>> parser) {
        if (name == null || name.isBlank() || input == null || input.isBlank() || parser == null) {
            throw new IllegalArgumentException("A split enricher needs a name, an input and a parser");
        }
        return new DumSplitEnricher(name, input, parser);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        List<String> texts = DumFallbackEnricher.texts(rec, input);
        if (texts.isEmpty()) {
            return Map.of();
        }
        Map<String, ?> parsed = parser.apply(texts.getFirst());
        if (parsed == null || parsed.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> contributions = new LinkedHashMap<>();
        parsed.forEach((field, value) -> {
            if (field != null && value != null) {
                contributions.put(field, DumSourced.of(value, input));
            }
        });
        return contributions;
    }
}
