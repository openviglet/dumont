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

import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumHttpJsonFetcher;
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonFetcher;
import com.viglet.dumont.connector.commons.spi.structured.oaipmh.DumDSpaceRest;
import com.viglet.dumont.connector.commons.spi.structured.oaipmh.DumHttpDSpaceRest;
import com.viglet.dumont.connector.commons.spi.structured.oaipmh.DumHttpOaiPmhFetcher;
import com.viglet.dumont.connector.commons.spi.structured.oaipmh.DumHttpOaiPmhFullTextFetcher;
import com.viglet.dumont.connector.commons.spi.structured.oaipmh.DumOaiPmhFetcher;
import com.viglet.dumont.connector.commons.spi.structured.oaipmh.DumOaiPmhFullTextFetcher;
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

    /** The JDK client a source with a JSON API manifest (D92) calls its API with. */
    @Bean
    DumJsonFetcher dumCatalogJsonFetcher() {
        return new DumHttpJsonFetcher();
    }

    /** The JDK client a source bound to an OAI-PMH repository (D315) harvests with. */
    @Bean
    DumOaiPmhFetcher dumCatalogOaiPmhFetcher() {
        return new DumHttpOaiPmhFetcher();
    }

    /**
     * What an OAI-PMH source with full text on (D315) reads its records' PDFs with: the
     * repository's own host only, 50 MiB and one million characters per document at most.
     */
    @Bean
    DumOaiPmhFullTextFetcher dumCatalogOaiPmhFullTextFetcher() {
        return new DumHttpOaiPmhFullTextFetcher();
    }

    /**
     * What an OAI-PMH source with the DSpace profile (D314) reads the repository's REST API with:
     * its own host only, paced, and with the full-text caps on bitstream content.
     */
    @Bean
    DumDSpaceRest dumCatalogDSpaceRest() {
        return new DumHttpDSpaceRest();
    }
}
