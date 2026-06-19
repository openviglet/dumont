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

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * A deterministic {@link DumEnricher} that derives or normalizes one field from another (roadmap
 * <strong>D04</strong>): canonical taxonomy mapping ({@code area} raw label &rarr; canonical area),
 * region derivation ({@code uf} state code &rarr; macro-region), unit/casing normalization, and the
 * like.
 *
 * <p>
 * It reads the {@code sourceField} value(s), runs each through a {@code mapping} function and writes
 * the results to {@code targetField}. The mapping returns {@link Optional#empty()} for any value it
 * cannot map — those values are <em>omitted</em>, never replaced with a placeholder, so the contract
 * holds: an unmapped value yields no contribution rather than a fabricated one. A multi-valued source
 * naturally produces a multi-valued contribution (blanks and unmapped entries dropped).
 *
 * <p>
 * No LLM, no I/O — pure and side-effect free, so it is the cheap, always-on counterpart to the
 * config-gated {@link DumClassificationEnricher}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumNormalizationEnricher implements DumEnricher {

    private final String name;
    private final String sourceField;
    private final String targetField;
    private final Function<String, Optional<String>> mapping;

    public DumNormalizationEnricher(String name, String sourceField, String targetField,
            Function<String, Optional<String>> mapping) {
        this.name = name;
        this.sourceField = sourceField;
        this.targetField = targetField;
        this.mapping = mapping;
    }

    /**
     * A lookup-table enricher: each source value is mapped through {@code table}; a value with no
     * entry is omitted. This is the canonical-taxonomy / region-derivation shape.
     */
    public static DumNormalizationEnricher lookup(String name, String sourceField, String targetField,
            Map<String, String> table) {
        return new DumNormalizationEnricher(name, sourceField, targetField,
                value -> Optional.ofNullable(table.get(value)));
    }

    /**
     * A free-function enricher: each source value runs through {@code mapping}, which returns
     * {@link Optional#empty()} for any value it cannot ground. The shape behind the shared
     * normalizers — e.g. {@code transform("alphabet", "title", "alphabet", DumTextNormalizers::firstLetter)}
     * or {@code transform("html2Text", "bodyHtml", "body", DumTextNormalizers::htmlToText)} — so a
     * derived/normalized field becomes a named enricher with no bespoke class.
     */
    public static DumNormalizationEnricher transform(String name, String sourceField,
            String targetField, Function<String, Optional<String>> mapping) {
        return new DumNormalizationEnricher(name, sourceField, targetField, mapping);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        if (!rec.has(sourceField)) {
            return Map.of();
        }
        List<Object> derived = new ArrayList<>();
        for (Object raw : asValues(rec.values().get(sourceField))) {
            mapping.apply(String.valueOf(raw))
                    .filter(mapped -> !mapped.isBlank())
                    .ifPresent(derived::add);
        }
        if (derived.isEmpty()) {
            return Map.of();
        }
        // One derived value -> a scalar (a single-valued target accepts it; a multi-valued target
        // wraps it). Several -> a list (a single-valued target would reject it, caught by the chain).
        return Map.of(targetField, derived.size() == 1 ? derived.getFirst() : derived);
    }

    private static Collection<?> asValues(Object value) {
        return value instanceof Collection<?> collection ? collection : List.of(value);
    }
}
