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

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.normalize.DumDateNormalizers;

/**
 * A {@link DumEnricher} that derives human-readable date-range labels from parallel sourced start /
 * end date fields (roadmap <strong>D04</strong> / <strong>D26</strong>).
 *
 * <p>
 * Generalizes the AEM god-class's {@code dateOfClassesText} — "format each class period as
 * {@code 15 Jan - 20 Fev 2024}, sorted chronologically". It pairs the i-th start with the i-th end,
 * sorts the pairs by start then end, and formats each via
 * {@link DumDateNormalizers#dateRangeText(Date, Date, ZoneId)}. Only complete pairs (both bounds
 * present) produce a label, so the derivation stays grounded — an unpaired or missing bound yields
 * no value rather than a half-formed range.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumDateRangeTextEnricher implements DumEnricher {

    private final String name;
    private final String targetField;
    private final String startField;
    private final String endField;
    private final ZoneId zone;

    public DumDateRangeTextEnricher(String name, String targetField, String startField,
            String endField, ZoneId zone) {
        this.name = name;
        this.targetField = targetField;
        this.startField = startField;
        this.endField = endField;
        this.zone = zone;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        List<Date> starts = dates(rec, startField);
        List<Date> ends = dates(rec, endField);
        int pairs = Math.min(starts.size(), ends.size());
        if (pairs == 0) {
            return Map.of();
        }
        List<String> labels = new ArrayList<>();
        java.util.stream.IntStream.range(0, pairs)
                .mapToObj(i -> new DatePair(starts.get(i), ends.get(i)))
                .sorted(Comparator.comparing(DatePair::start).thenComparing(DatePair::end))
                .forEach(pair -> DumDateNormalizers.dateRangeText(pair.start(), pair.end(), zone)
                        .ifPresent(labels::add));
        return labels.isEmpty() ? Map.of() : Map.of(targetField, labels);
    }

    private static List<Date> dates(DumStructuredRecord rec, String field) {
        if (!rec.has(field)) {
            return List.of();
        }
        return asValues(rec.values().get(field)).stream()
                .filter(Date.class::isInstance)
                .map(Date.class::cast)
                .toList();
    }

    private static Collection<?> asValues(Object value) {
        return value instanceof Collection<?> collection ? collection : List.of(value);
    }

    private record DatePair(Date start, Date end) {
    }
}
