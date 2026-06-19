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
import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * A {@link DumEnricher} that derives a boolean flag as the logical OR over one or more sourced
 * boolean fields (roadmap <strong>D04</strong> / <strong>D26</strong>).
 *
 * <p>
 * Generalizes the AEM god-class's {@code inscricaoAberta} ("is enrolment open"): true when <em>any</em>
 * contributing flag (open contest, open manual offer) is true. The flag is a classification over
 * sourced data, not a sourced attribute, so it may legitimately carry a {@code defaultWhenAbsent}
 * verdict for a record that declares none of the inputs — pass {@code null} to instead leave the
 * field absent (strict grounding), or {@link Boolean#FALSE} to mirror the legacy "closed by default"
 * behaviour.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumBooleanAnyEnricher implements DumEnricher {

    private final String name;
    private final String targetField;
    private final List<String> sourceFields;
    private final Boolean defaultWhenAbsent;

    public DumBooleanAnyEnricher(String name, String targetField, List<String> sourceFields,
            Boolean defaultWhenAbsent) {
        this.name = name;
        this.targetField = targetField;
        this.sourceFields = List.copyOf(sourceFields);
        this.defaultWhenAbsent = defaultWhenAbsent;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<String, Object> enrich(DumStructuredSource source, DumStructuredRecord rec) {
        boolean anyPresent = false;
        for (String field : sourceFields) {
            if (!rec.has(field)) {
                continue;
            }
            for (Object value : asValues(rec.values().get(field))) {
                anyPresent = true;
                if (isTrue(value)) {
                    return Map.of(targetField, Boolean.TRUE);
                }
            }
        }
        if (anyPresent) {
            return Map.of(targetField, Boolean.FALSE);
        }
        return defaultWhenAbsent == null ? Map.of() : Map.of(targetField, defaultWhenAbsent);
    }

    private static boolean isTrue(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static Collection<?> asValues(Object value) {
        return value instanceof Collection<?> collection ? collection : List.of(value);
    }
}
