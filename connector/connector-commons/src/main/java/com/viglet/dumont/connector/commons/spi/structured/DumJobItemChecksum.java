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

package com.viglet.dumont.connector.commons.spi.structured;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/**
 * The checksum of what a structured job item publishes (roadmap <strong>D127</strong>): its
 * attributes, its specs, its sites and its locale, in a canonical order, so an unchanged record
 * hashes the same on every run and the reindex strategy can tell it from a changed one.
 *
 * <p>
 * Canonical means independent of map and spec order: map keys are sorted at every depth and specs
 * are sorted by name. Collections keep their order, since a reordered multi-valued field is a
 * different document.
 */
final class DumJobItemChecksum {

    private static final char SEPARATOR = '\u0001';

    private DumJobItemChecksum() {
    }

    static String of(TurSNJobItem item) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("locale=").append(item.getLocale()).append(SEPARATOR);
        canonical.append("sites=").append(canonical(item.getSiteNames())).append(SEPARATOR);
        canonical.append("attributes=").append(canonical(item.getAttributes())).append(SEPARATOR);
        canonical.append("specs=").append(specs(item.getSpecs()));
        return sha256(canonical.toString());
    }

    private static String specs(List<TurSNJobAttributeSpec> specs) {
        if (specs == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        specs.stream()
                .sorted(Comparator.comparing(TurSNJobAttributeSpec::getName,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(spec -> out.append(spec.getName()).append(':').append(spec.getType())
                        .append(':').append(spec.isMandatory()).append(':').append(spec.isMultiValued())
                        .append(':').append(spec.isFacet()).append(':').append(spec.getDescription())
                        .append(':').append(canonical(spec.getFacetName())).append(SEPARATOR));
        return out.toString();
    }

    private static String canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, String> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), canonical(v)));
            return sorted.toString();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(DumJobItemChecksum::canonical).toList().toString();
        }
        if (value instanceof Object[] array) {
            return canonical(List.of(array));
        }
        return Objects.toString(value, "");
    }

    private static String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }
}
