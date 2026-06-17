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

import java.util.ArrayList;
import java.util.List;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumColumnRule;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumTabularListingStrategy;

/**
 * A versioned, parameterized <em>tabular-listing recipe</em> (roadmap <strong>D19</strong>): the
 * shared, reusable shape of a {@link DumTabularListingStrategy} catalog — its schema, row selector,
 * column rules, id field and taxonomy applications — captured once and applied to many sources by
 * reference.
 *
 * <p>
 * This is the concrete artifact behind "one SIGAA {@code TabularListing} recipe &rarr; all federals"
 * (D11). Everything that is the <em>same</em> across the SIGAA institutions lives in the recipe; the
 * only thing each source supplies is its {@link DumRecipeParameters} (listing URLs, sites, locale).
 * {@link #strategy(DumHtmlFetcher, List)} binds the recipe's structural config to one source's
 * listing URLs to produce a ready strategy — so registering a new recipe version and re-resolving a
 * source re-applies the change to every source built from that recipe.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumTabularRecipe {

    private final String name;
    private final int version;
    private final DumStructuredSchema schema;
    private final String rowSelector;
    private final List<DumColumnRule> columnRules;
    private final String idField;
    private final List<DumTaxonomyApplication> taxonomyApplications;

    private DumTabularRecipe(Builder builder) {
        this.name = builder.name;
        this.version = builder.version;
        this.schema = builder.schema;
        this.rowSelector = builder.rowSelector;
        this.columnRules = List.copyOf(builder.columnRules);
        this.idField = builder.idField;
        this.taxonomyApplications = List.copyOf(builder.taxonomyApplications);
    }

    public static Builder builder(String name, int version, DumStructuredSchema schema) {
        return new Builder(name, version, schema);
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    /** This recipe's identity as a pinned {@link DumRecipeRef} ({@code name@version}). */
    public DumRecipeRef ref() {
        return DumRecipeRef.of(name, version);
    }

    public DumStructuredSchema schema() {
        return schema;
    }

    /** The taxonomy applications this recipe declares, in order. */
    public List<DumTaxonomyApplication> taxonomyApplications() {
        return taxonomyApplications;
    }

    /**
     * Bind this recipe's structural config to one source's listing URLs, producing a ready
     * {@link DumTabularListingStrategy}. Called fresh per source resolution, so the strategy always
     * reflects the recipe version currently registered in the library.
     */
    public DumTabularListingStrategy strategy(DumHtmlFetcher fetcher, List<String> listingUrls) {
        DumTabularListingStrategy.Builder strategy =
                DumTabularListingStrategy.builder(fetcher, schema)
                        .listings(listingUrls)
                        .rows(rowSelector);
        columnRules.forEach(strategy::column);
        if (idField != null) {
            strategy.idField(idField);
        }
        return strategy.build();
    }

    public static final class Builder {
        private final String name;
        private final int version;
        private final DumStructuredSchema schema;
        private final List<DumColumnRule> columnRules = new ArrayList<>();
        private final List<DumTaxonomyApplication> taxonomyApplications = new ArrayList<>();
        private String rowSelector;
        private String idField;

        private Builder(String name, int version, DumStructuredSchema schema) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Recipe name is required");
            }
            if (version <= 0) {
                throw new IllegalArgumentException("Recipe version must be positive: " + version);
            }
            if (schema == null) {
                throw new IllegalArgumentException("Recipe schema is required");
            }
            this.name = name.trim();
            this.version = version;
            this.schema = schema;
        }

        /** The CSS selector for the data rows (e.g. {@code "table.listagem > tbody > tr"}). */
        public Builder rows(String rowSelector) {
            this.rowSelector = rowSelector;
            return this;
        }

        public Builder column(DumColumnRule rule) {
            this.columnRules.add(rule);
            return this;
        }

        /** Map the cell at {@code columnIndex} (text) to {@code field}. */
        public Builder column(String field, int columnIndex) {
            return column(DumColumnRule.column(field, columnIndex));
        }

        /** The field whose value identifies each row (positional {@code row-N} id when unset/blank). */
        public Builder idField(String idField) {
            this.idField = idField;
            return this;
        }

        /** Apply {@code taxonomy} over {@code sourceField}, writing the canonical value to {@code targetField}. */
        public Builder taxonomy(DumRecipeRef taxonomy, String sourceField, String targetField) {
            this.taxonomyApplications
                    .add(new DumTaxonomyApplication(taxonomy, sourceField, targetField));
            return this;
        }

        public DumTabularRecipe build() {
            if (rowSelector == null || rowSelector.isBlank()) {
                throw new IllegalStateException("A row selector is required");
            }
            if (columnRules.isEmpty()) {
                throw new IllegalStateException("At least one column rule is required");
            }
            return new DumTabularRecipe(this);
        }
    }
}
