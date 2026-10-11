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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<String, Map<String, String>> attributes;
    private final String source;
    private final String downloadedAt;
    private final Map<String, Map<String, String>> attributeTables = new ConcurrentHashMap<>();

    private DumTaxonomy(Builder builder) {
        this.name = builder.name;
        this.version = builder.version;
        this.table = Map.copyOf(builder.table);
        Map<String, Map<String, String>> copied = new LinkedHashMap<>();
        builder.attributes.forEach((raw, row) -> copied.put(raw, Map.copyOf(row)));
        this.attributes = Collections.unmodifiableMap(copied);
        this.source = builder.source;
        this.downloadedAt = builder.downloadedAt;
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

    /**
     * The other columns of a raw label's row (roadmap <strong>D99</strong>), e.g. a municipality's
     * {@code uf}; empty for an unmapped label or a flat taxonomy.
     */
    public Map<String, String> attributes(String raw) {
        return raw == null ? Map.of() : attributes.getOrDefault(raw, Map.of());
    }

    /** One attribute of a raw label's row, or {@link Optional#empty()} when absent (D99). */
    public Optional<String> attribute(String raw, String attribute) {
        return Optional.ofNullable(attributes(raw).get(attribute));
    }

    /**
     * Raw label &rarr; one attribute, for every row that carries it (D99): the table a taxonomy
     * application reads when it writes an attribute instead of the canonical value. Built once per
     * attribute.
     */
    public Map<String, String> attributeTable(String attribute) {
        return attributeTables.computeIfAbsent(attribute, name -> {
            Map<String, String> built = new LinkedHashMap<>();
            attributes.forEach((raw, row) -> {
                String value = row.get(name);
                if (value != null) {
                    built.put(raw, value);
                }
            });
            return Map.copyOf(built);
        });
    }

    /** Where the table came from (a URL or a dataset name), or {@code null} when not declared. */
    public String source() {
        return source;
    }

    /** When the table was downloaded from its {@link #source()}, as declared, or {@code null}. */
    public String downloadedAt() {
        return downloadedAt;
    }

    /** How many raw labels the taxonomy maps. */
    public int size() {
        return table.size();
    }

    public static final class Builder {
        private final String name;
        private final int version;
        private final Map<String, String> table = new LinkedHashMap<>();
        private final Map<String, Map<String, String>> attributes = new LinkedHashMap<>();
        private String source;
        private String downloadedAt;

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

        /**
         * Map a raw label to its canonical form with the rest of its row (roadmap
         * <strong>D99</strong>). Blank attribute values are left out, so an attribute a row lacks is
         * absent rather than {@code ""}.
         */
        public Builder row(String raw, String canonical, Map<String, String> rowAttributes) {
            map(raw, canonical);
            Map<String, String> kept = new LinkedHashMap<>();
            if (rowAttributes != null) {
                rowAttributes.forEach((attribute, value) -> {
                    if (attribute != null && !attribute.isBlank() && value != null && !value.isBlank()) {
                        kept.put(attribute, value);
                    }
                });
            }
            if (kept.isEmpty()) {
                attributes.remove(raw);
            } else {
                attributes.put(raw, kept);
            }
            return this;
        }

        /** Where the table came from, e.g. the dataset URL (D99). */
        public Builder source(String source) {
            this.source = source == null || source.isBlank() ? null : source.trim();
            return this;
        }

        /** When the table was downloaded from its source, as the file declares it (D99). */
        public Builder downloadedAt(String downloadedAt) {
            this.downloadedAt = downloadedAt == null || downloadedAt.isBlank() ? null : downloadedAt.trim();
            return this;
        }

        public DumTaxonomy build() {
            return new DumTaxonomy(this);
        }
    }
}
