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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumJsoupFetcher;

/**
 * Wires the native-migration machinery for the catalog connector (roadmap <strong>D11</strong>): the
 * {@link DumRecipeLibrary} holding the registered migration recipes (starting with the SIGAA-federal
 * leva) and the production {@link DumHtmlFetcher} the native strategies crawl with.
 *
 * <p>
 * Registering a new recipe version (or a new leva's recipe) here re-applies to every bound source on
 * its next run, since {@code DumRecipeSourceProvider} materializes sources from the library's current
 * state — the D19 "applied by reference" guarantee.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Configuration
public class DumCatalogRecipeConfig {

    /** The recipe registry, seeded with the migration recipes the catalog connector ships. */
    @Bean
    DumRecipeLibrary dumCatalogRecipeLibrary() {
        return new DumRecipeLibrary().register(DumSigaaFederalRecipe.recipe());
    }

    /** The production jsoup fetcher native strategies crawl public listings with. */
    @Bean
    DumHtmlFetcher dumCatalogHtmlFetcher() {
        return new DumJsoupFetcher();
    }
}
