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

package com.viglet.dumont.connector.plugin.catalog.source;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeParameters;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeRef;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.dumont.connector.plugin.catalog.DumCatalogPlugin;
import com.viglet.dumont.connector.plugin.catalog.DumCatalogSchema;
import com.viglet.dumont.connector.plugin.catalog.fetch.DumCatalogFetcher;
import com.viglet.dumont.connector.plugin.catalog.migration.DumCatalogMigrationState;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves catalog sources from the JPA store for the generic structured indexing loop, picking the
 * indexing leg per source (roadmap <strong>D11</strong>):
 *
 * <ul>
 * <li>a {@link DumCatalogMigrationState#SIDECAR} source resolves to a {@link DumCatalogStructuredSource}
 * — the D10 bridge over the external engine's normalized JSON;</li>
 * <li>a {@link DumCatalogMigrationState#NATIVE} source resolves to a recipe-materialized native
 * strategy (D19), so the Python leg is retired for it. If its recipe is not registered, resolution
 * falls back to the sidecar and logs — the D09 safe-run discipline, never a failed run.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class DumCatalogSourceProvider implements DumStructuredSourceProvider {

    private final DumCatalogSourceRepository sourceRepository;
    private final DumCatalogFetcher fetcher;
    private final DumRecipeLibrary recipeLibrary;
    private final DumHtmlFetcher htmlFetcher;

    public DumCatalogSourceProvider(DumCatalogSourceRepository sourceRepository,
            DumCatalogFetcher fetcher, DumRecipeLibrary recipeLibrary, DumHtmlFetcher htmlFetcher) {
        this.sourceRepository = sourceRepository;
        this.fetcher = fetcher;
        this.recipeLibrary = recipeLibrary;
        this.htmlFetcher = htmlFetcher;
    }

    @Override
    public String getProviderName() {
        return DumCatalogPlugin.PROVIDER_NAME;
    }

    @Override
    public Collection<String> sourceNames() {
        return sourceRepository.findAll().stream().map(DumCatalogSource::getName).toList();
    }

    @Override
    public Optional<DumStructuredSource> resolve(String sourceName) {
        return sourceRepository.findByName(sourceName).map(this::resolve);
    }

    /** Pick the running source for {@code source}: native when flipped and bound, else the sidecar. */
    public DumStructuredSource resolve(DumCatalogSource source) {
        if (isNative(source)) {
            Optional<DumStructuredSource> nativeSource = nativeSource(source);
            if (nativeSource.isPresent()) {
                return nativeSource.get();
            }
            log.warn("Catalog source '{}' is flagged NATIVE but recipe '{}' is not registered — "
                    + "falling back to the sidecar", source.getName(), source.getNativeRecipe());
        }
        return sidecarSource(source);
    }

    /** The D10 sidecar source: the bridge over the external engine's normalized JSON. */
    public DumCatalogStructuredSource sidecarSource(DumCatalogSource source) {
        return new DumCatalogStructuredSource(source, DumCatalogSchema.schema(), fetcher);
    }

    /**
     * The native recipe-materialized source for {@code source}, or {@link Optional#empty()} when it
     * has no native binding (recipe/listing URL) or the recipe is not registered in the library.
     */
    public Optional<DumStructuredSource> nativeSource(DumCatalogSource source) {
        if (!hasNativeBinding(source)) {
            return Optional.empty();
        }
        DumRecipeRef ref = DumRecipeRef.parse(source.getNativeRecipe());
        DumRecipeParameters.Builder params = DumRecipeParameters.builder(source.getName())
                .listing(source.getNativeListingUrl()).locale(localeOf(source));
        if (source.getTurSNSites() != null) {
            source.getTurSNSites().forEach(params::site);
        }
        return recipeLibrary.materialize(ref, params.build(), DumCatalogPlugin.PROVIDER_NAME,
                htmlFetcher);
    }

    /** Whether this source should index natively: flagged {@code NATIVE} and bound to a recipe. */
    public boolean isNative(DumCatalogSource source) {
        return source.getMigrationState() == DumCatalogMigrationState.NATIVE
                && hasNativeBinding(source);
    }

    private static boolean hasNativeBinding(DumCatalogSource source) {
        return notBlank(source.getNativeRecipe()) && notBlank(source.getNativeListingUrl());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static Locale localeOf(DumCatalogSource source) {
        String locale = source.getLocale();
        if (locale == null || locale.isBlank()) {
            return Locale.of("pt", "BR");
        }
        String[] parts = locale.split("[_-]", 2);
        return parts.length == 2 ? Locale.of(parts[0], parts[1]) : Locale.of(parts[0]);
    }
}
