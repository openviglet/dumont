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

/**
 * One taxonomy application declared by a {@link DumTabularRecipe} (roadmap <strong>D19</strong>):
 * "normalize the value of {@code sourceField} through the taxonomy {@code taxonomy} and write the
 * canonical result to {@code targetField}".
 *
 * <p>
 * The taxonomy is held <em>by reference</em>, not as an inlined map — the recipe says <em>which</em>
 * taxonomy to apply, the {@link DumRecipeLibrary} resolves the current version at enrichment time
 * (see {@link DumTaxonomyEnricher}). That indirection is what lets a taxonomy bump re-apply to every
 * source whose recipe declares this application, without rebuilding the enrichment chain.
 *
 * @param taxonomy    the taxonomy to apply, by reference (pinned or {@link DumRecipeRef#LATEST_VERSION})
 * @param sourceField the field whose raw value is normalized
 * @param targetField the field the canonical value is written to (may equal {@code sourceField})
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record DumTaxonomyApplication(DumRecipeRef taxonomy, String sourceField, String targetField) {

    public DumTaxonomyApplication {
        if (taxonomy == null) {
            throw new IllegalArgumentException("Taxonomy application needs a taxonomy ref");
        }
        if (sourceField == null || sourceField.isBlank()) {
            throw new IllegalArgumentException("Taxonomy application needs a source field");
        }
        if (targetField == null || targetField.isBlank()) {
            throw new IllegalArgumentException("Taxonomy application needs a target field");
        }
    }

    /** A stable enricher name for ordering/diagnostics, e.g. {@code taxonomy:area->canonicalArea}. */
    public String enricherName() {
        return "taxonomy:" + taxonomy.name() + ":" + sourceField + "->" + targetField;
    }
}
