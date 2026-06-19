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

package com.viglet.dumont.connector.commons.spi.structured.resolve;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;

/**
 * Derives the <strong>canonical id</strong> of a record (roadmap <strong>D18</strong>) — the stable
 * real-world identity by which the same entity arriving from different sources is recognised as one.
 *
 * <p>
 * The same course across institution mirrors, or one item under a canonical URL and its
 * tracking-parameter variants, all map to the <em>same</em> canonical id so cross-source resolution
 * can collapse them into a single document. The key is computed from fields the source actually
 * supplied — when none are present it returns {@link Optional#empty()} and the record is published on
 * its own (never merged on a guessed identity). Nothing is fabricated.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@FunctionalInterface
public interface DumCanonicalKey {

    /**
     * The canonical id for {@code record}, or {@link Optional#empty()} when the record carries no
     * value for the key (it then indexes under its own id, unmerged).
     */
    Optional<String> canonicalId(DumStructuredSource source, DumStructuredRecord rec);

    /**
     * A canonical key built from one or more declared natural-key fields (e.g. a course code, or
     * {@code institution} + {@code code}). The values are read from the record, normalized
     * (trimmed, lower-cased, inner whitespace collapsed) and joined — so the same entity keyed the
     * same way across sources resolves to the same id. Returns empty unless <em>every</em> key field
     * is present, so a partially-keyed record is never merged on an ambiguous identity.
     */
    static DumCanonicalKey field(String... fields) {
        List<String> keyFields = List.of(fields);
        if (keyFields.isEmpty()) {
            throw new IllegalArgumentException("A field canonical key needs at least one field");
        }
        return (source, rec) -> {
            StringBuilder id = new StringBuilder();
            for (String field : keyFields) {
                String part = normalize(rec.values().get(field));
                if (part == null) {
                    return Optional.empty();
                }
                if (!id.isEmpty()) {
                    id.append('|');
                }
                id.append(part);
            }
            return Optional.of(id.toString());
        };
    }

    private static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        Object first = value instanceof Collection<?> collection
                ? collection.stream().findFirst().orElse(null)
                : value;
        if (first == null) {
            return null;
        }
        String text = first.toString().trim().toLowerCase().replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text;
    }
}
