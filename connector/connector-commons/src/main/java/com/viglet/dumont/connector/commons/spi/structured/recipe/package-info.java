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

/**
 * Reusable recipe &amp; taxonomy library (roadmap <strong>D19</strong>).
 *
 * <p>
 * Versions shared, parameterized extraction recipes and canonical taxonomy maps as first-class
 * artifacts, applied <em>by reference</em> instead of copied per source — the mechanism behind "one
 * SIGAA {@code TabularListing} recipe &rarr; all federals" (D11). A
 * {@link com.viglet.dumont.connector.commons.spi.structured.recipe.DumTabularRecipe} captures the
 * shared shape (schema, row selector, column rules, taxonomy applications); a
 * {@link com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy} is a versioned
 * canonical map; both live in the
 * {@link com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary} keyed by
 * {@link com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeRef name@version}.
 *
 * <p>
 * A source supplies only its small
 * {@link com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeParameters} (listing
 * URLs, sites, locale); the
 * {@link com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeSourceProvider}
 * materializes the {@code DumStructuredSource} on demand from the library's current state, so a
 * recipe bump re-applies to every bound source on the next run and a taxonomy bump re-applies live
 * through the library-backed
 * {@link com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomyEnricher}. All
 * grounding-safe: a recipe normalizes via its taxonomies, it never fabricates a sourced field.
 */
package com.viglet.dumont.connector.commons.spi.structured.recipe;
