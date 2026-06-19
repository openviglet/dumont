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

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.normalize.DumDateNormalizers;

/**
 * A {@link DumEnricher} that derives a single sort date: the earliest date, across one or more
 * sourced date fields, that is not before "today" in a given zone (roadmap <strong>D04</strong> /
 * <strong>D26</strong>).
 *
 * <p>
 * Generalizes the AEM god-class's {@code dateSort} — "the earliest future class-start date used to
 * order results". The future boundary is computed via
 * {@link DumDateNormalizers#startOfDay(Instant, ZoneId)} from an injected clock, so the result is
 * deterministic and unit-testable. When no sourced date qualifies, an optional {@code fallback}
 * sentinel is emitted (the legacy far-future default that sorts such records last); pass
 * {@code null} to leave the field absent. The value is a derived sort key, never a sourced
 * attribute, so the sentinel does not breach grounding.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumEarliestDateEnricher implements DumEnricher {

    private final String name;
    private final String targetField;
    private final List<String> sourceFields;
    private final Supplier<Instant> clock;
    private final ZoneId zone;
    private final Date fallback;

    public DumEarliestDateEnricher(String name, String targetField, List<String> sourceFields,
            Supplier<Instant> clock, ZoneId zone, Date fallback) {
        this.name = name;
        this.targetField = targetField;
        this.sourceFields = List.copyOf(sourceFields);
        this.clock = clock;
        this.zone = zone;
        this.fallback = fallback == null ? null : new Date(fallback.getTime());
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        Instant today = DumDateNormalizers.startOfDay(clock.get(), zone);
        Date earliest = sourceFields.stream()
                .filter(rec::has)
                .flatMap(field -> asValues(rec.values().get(field)).stream())
                .filter(Date.class::isInstance)
                .map(Date.class::cast)
                .filter(date -> !date.toInstant().isBefore(today))
                .min(Comparator.naturalOrder())
                .orElse(fallback);
        return earliest == null ? Map.of() : Map.of(targetField, earliest);
    }

    private static Collection<?> asValues(Object value) {
        return value instanceof Collection<?> collection ? collection : List.of(value);
    }
}
