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

package com.viglet.dumont.connector.commons.ground;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The grounding contract, enforced as a reusable operation on attribute maps.
 *
 * <p>
 * Dumont publishes a field <em>only when the source actually declares a value for it</em>. An absent
 * field is omitted from the document — never sent as {@code ""} or an empty list — because empty
 * values pollute facets and break downstream "is this field present?" logic, and Dumont never
 * invents a value to fill a schema.
 *
 * <p>
 * The structured-source SPI (roadmap <strong>D01</strong>) grounds values at the point a
 * {@code DumStructuredRecord} is built. This utility lifts the same rule to a single reusable place
 * so it can be enforced <em>end-to-end</em> (roadmap <strong>D03</strong>): every
 * {@code TurSNJobItem} — including those built as loose attribute maps by the web crawler, AEM, DB
 * and assets connectors — is grounded once at the indexing boundary, so no connector can leak an
 * empty value into the search backend regardless of how it assembled its attributes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumGrounding {

    private DumGrounding() {
        throw new IllegalStateException("Grounding utility class");
    }

    /**
     * Whether a value must be omitted under the grounding contract: {@code null}, a blank
     * {@link CharSequence}, or a collection whose every entry is itself blank.
     */
    public static boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence cs) {
            return cs.toString().isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().allMatch(DumGrounding::isBlank);
        }
        return false;
    }

    /**
     * The grounded form of a single value, or {@code null} if it must be omitted entirely. A
     * collection is returned with its blank entries removed; if nothing survives, the whole value is
     * dropped (returns {@code null}). Scalars are returned unchanged unless blank.
     */
    public static Object ground(Object value) {
        if (value instanceof Collection<?> collection) {
            List<Object> kept = new ArrayList<>();
            for (Object entry : collection) {
                if (!isBlank(entry)) {
                    kept.add(entry);
                }
            }
            return kept.isEmpty() ? null : kept;
        }
        return isBlank(value) ? null : value;
    }

    /**
     * A copy of {@code attributes} with every ungrounded entry removed — blank strings, {@code null}s
     * and empty/all-blank collections are dropped so the field stays absent rather than reaching the
     * index as an empty value. Declaration order is preserved. A {@code null} or empty input is
     * returned as-is.
     */
    public static Map<String, Object> groundAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return attributes;
        }
        Map<String, Object> grounded = new LinkedHashMap<>();
        attributes.forEach((name, value) -> {
            Object groundedValue = ground(value);
            if (groundedValue != null) {
                grounded.put(name, groundedValue);
            }
        });
        return grounded;
    }
}
