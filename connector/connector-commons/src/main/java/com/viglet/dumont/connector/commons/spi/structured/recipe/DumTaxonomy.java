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

package com.viglet.dumont.connector.commons.spi.structured.recipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A canonical taxonomy map as a first-class <em>versioned</em> artifact (roadmap
 * <strong>D19</strong>): a named, immutable mapping from raw source labels to their canonical form
 * (raw {@code area} label &rarr; canonical area, {@code uf} code &rarr; macro-region, …).
 *
 * <p>
 * Sharing the map by reference (instead of copying a {@code Map} into every source's enrichment
 * config) is what lets a taxonomy correction re-apply everywhere: register a new version in the
 * {@link DumRecipeLibrary} and every source whose recipe references the taxonomy picks it up on the
 * next run. A {@link DumTabularRecipe} applies a taxonomy through a library-backed enricher, so the
 * grounding contract still holds — a raw label with no canonical entry maps to
 * {@link Optional#empty()} and is <em>omitted</em>, never replaced with a placeholder.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumTaxonomy {

    private final String name;
    private final int version;
    private final Map<String, String> table;

    private DumTaxonomy(String name, int version, Map<String, String> table) {
        this.name = name;
        this.version = version;
        this.table = Map.copyOf(table);
    }

    public static Builder builder(String name, int version) {
        return new Builder(name, version);
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    /** This taxonomy's identity as a pinned {@link DumRecipeRef} ({@code name@version}). */
    public DumRecipeRef ref() {
        return DumRecipeRef.of(name, version);
    }

    /** The raw &rarr; canonical mapping, immutable. */
    public Map<String, String> table() {
        return table;
    }

    /** The canonical form of a raw label, or {@link Optional#empty()} when the label is unmapped. */
    public Optional<String> canonical(String raw) {
        return raw == null ? Optional.empty() : Optional.ofNullable(table.get(raw));
    }

    public static final class Builder {
        private final String name;
        private final int version;
        private final Map<String, String> table = new LinkedHashMap<>();

        private Builder(String name, int version) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Taxonomy name is required");
            }
            if (version <= 0) {
                throw new IllegalArgumentException("Taxonomy version must be positive: " + version);
            }
            this.name = name.trim();
            this.version = version;
        }

        /** Map a raw label to its canonical form. A blank raw key or canonical value is rejected. */
        public Builder map(String raw, String canonical) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("Taxonomy raw key is required");
            }
            if (canonical == null || canonical.isBlank()) {
                throw new IllegalArgumentException(
                        "Taxonomy canonical value for '" + raw + "' is required");
            }
            table.put(raw, canonical);
            return this;
        }

        public Builder mapAll(Map<String, String> entries) {
            entries.forEach(this::map);
            return this;
        }

        public DumTaxonomy build() {
            return new DumTaxonomy(name, version, table);
        }
    }
}
