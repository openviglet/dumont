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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * A {@link DumEnricher} that flattens one or more sourced fields into a single de-duplicated,
 * multi-valued target field (roadmap <strong>D04</strong> / <strong>D26</strong>).
 *
 * <p>
 * Generalizes the AEM god-class's {@code preco} aggregation — "collect every open-enrolment price
 * across contests and manual offers, drop duplicates". The derivation reads only fields the source
 * already grounded and merely re-projects them, so it is grounding-safe by construction: it never
 * invents a value, and an absent source contributes nothing. First-seen order is preserved.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumDistinctValuesEnricher implements DumEnricher {

    private final String name;
    private final String targetField;
    private final List<String> sourceFields;

    public DumDistinctValuesEnricher(String name, String targetField, List<String> sourceFields) {
        this.name = name;
        this.targetField = targetField;
        this.sourceFields = List.copyOf(sourceFields);
    }

    /** Aggregate from a single source field. */
    public static DumDistinctValuesEnricher of(String name, String targetField, String sourceField) {
        return new DumDistinctValuesEnricher(name, targetField, List.of(sourceField));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        Set<Object> distinct = new LinkedHashSet<>();
        for (String field : sourceFields) {
            if (rec.has(field)) {
                distinct.addAll(asValues(rec.values().get(field)));
            }
        }
        return distinct.isEmpty() ? Map.of() : Map.of(targetField, List.copyOf(distinct));
    }

    private static Collection<?> asValues(Object value) {
        return value instanceof Collection<?> collection ? collection : List.of(value);
    }
}
