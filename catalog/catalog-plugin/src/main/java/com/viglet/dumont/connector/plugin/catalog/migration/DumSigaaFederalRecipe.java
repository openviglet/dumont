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

package com.viglet.dumont.connector.plugin.catalog.migration;

import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeRef;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTabularRecipe;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumColumnRule;
import com.viglet.dumont.connector.plugin.catalog.DumCatalogSchema;

/**
 * The shared SIGAA-federal {@code TabularListing} recipe — the first and highest-leverage migration
 * leva (roadmap <strong>D11</strong>): the federal universities run the same SIGAA course-listing
 * table, so a single recipe drives all of them and each source supplies only its listing URL.
 *
 * <p>
 * The recipe is published on the {@link DumCatalogSchema catalog schema} so a native-indexed source
 * emits the <em>same</em> document shape as the sidecar it replaces — which is exactly what lets the
 * {@code DumParityGate} compare the two runs on URL coverage and the count. The selectors here are a
 * conservative v1 starter; the leg-by-leg discipline is to refine them per institution and re-gate
 * until parity, then flip. Grounding holds throughout: a cell that does not match is omitted, never
 * fabricated.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumSigaaFederalRecipe {

    private DumSigaaFederalRecipe() {
    }

    /** The recipe name federal sources bind to. */
    public static final String RECIPE_NAME = "sigaa-federal";

    /** The current recipe version registered in the library. */
    public static final int VERSION = 1;

    /** A reference tracking the latest registered version of this recipe. */
    public static DumRecipeRef ref() {
        return DumRecipeRef.latest(RECIPE_NAME);
    }

    /**
     * Build the {@code sigaa-federal} recipe (v1). Course rows live in SIGAA's
     * {@code table.listagem}; the course name links to its public detail page (the grounded,
     * mandatory {@code url}), and the URL doubles as the stable row id.
     */
    public static DumTabularRecipe recipe() {
        return DumTabularRecipe.builder(RECIPE_NAME, VERSION, DumCatalogSchema.schema())
                .rows("table.listagem > tbody > tr")
                .column(DumColumnRule.selector(DumCatalogSchema.TITLE, "td.nome a, td:nth-child(1) a"))
                .column(DumColumnRule.selector(DumCatalogSchema.URL, "td.nome a, td:nth-child(1) a",
                        "abs:href"))
                .column(DumColumnRule.selector(DumCatalogSchema.LEVEL, "td.nivel"))
                .column(DumColumnRule.selector(DumCatalogSchema.MODALITY, "td.modalidade"))
                .idField(DumCatalogSchema.URL)
                .build();
    }
}
