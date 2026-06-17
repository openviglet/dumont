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

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumTabularListingStrategy;

/**
 * The {@link DumStructuredSource} a {@link DumTabularRecipe} materializes against one source's
 * {@link DumRecipeParameters} (roadmap <strong>D19</strong>).
 *
 * <p>
 * It owns nothing structural of its own: its {@link #schema()} is the recipe's schema and its
 * {@link #discover()}/{@link #extract(String)} delegate to a {@link DumTabularListingStrategy} the
 * recipe built from this source's listing URLs. Created fresh on each resolution, so it always
 * reflects the recipe version currently registered — which is how a recipe bump re-applies.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
final class DumRecipeTabularSource implements DumStructuredSource {

    private final String providerName;
    private final DumRecipeParameters parameters;
    private final DumStructuredSchema schema;
    private final DumTabularListingStrategy strategy;

    DumRecipeTabularSource(String providerName, DumTabularRecipe recipe,
            DumRecipeParameters parameters,
            com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher fetcher) {
        this.providerName = providerName;
        this.parameters = parameters;
        this.schema = recipe.schema();
        this.strategy = recipe.strategy(fetcher, parameters.listingUrls());
    }

    @Override
    public String getSourceName() {
        return parameters.sourceName();
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public Collection<String> getSiteNames() {
        return parameters.siteNames();
    }

    @Override
    public DumStructuredSchema schema() {
        return schema;
    }

    @Override
    public Locale getLocale() {
        return parameters.locale();
    }

    @Override
    public List<String> discover() {
        return strategy.discover();
    }

    @Override
    public Optional<DumStructuredRecord> extract(String contentId) {
        return strategy.extract(contentId);
    }
}
