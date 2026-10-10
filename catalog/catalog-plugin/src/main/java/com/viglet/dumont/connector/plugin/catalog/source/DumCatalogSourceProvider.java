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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiManifest;
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiSource;
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonFetcher;
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
    private final DumJsonFetcher jsonFetcher;

    public DumCatalogSourceProvider(DumCatalogSourceRepository sourceRepository,
            DumCatalogFetcher fetcher, DumRecipeLibrary recipeLibrary, DumHtmlFetcher htmlFetcher,
            DumJsonFetcher jsonFetcher) {
        this.sourceRepository = sourceRepository;
        this.fetcher = fetcher;
        this.recipeLibrary = recipeLibrary;
        this.htmlFetcher = htmlFetcher;
        this.jsonFetcher = jsonFetcher;
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
        return sourceRepository.findByName(sourceName).flatMap(source -> {
            try {
                return Optional.of(resolve(source));
            } catch (IllegalArgumentException e) {
                // Only a JSON API binding can be refused here; the API validates it on save, so this
                // is a row written around the API (an import, a hand edit).
                log.error("Catalog source '{}' cannot run its JSON API manifest: {}", sourceName,
                        e.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * Pick the running source for {@code source}: its JSON API manifest when it has one (D92), else
     * native when flipped and bound, else the sidecar.
     *
     * @throws IllegalArgumentException when the source's JSON API manifest or secrets cannot run
     */
    public DumStructuredSource resolve(DumCatalogSource source) {
        if (isJsonApi(source)) {
            return jsonApiSource(source);
        }
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

    /**
     * The JSON API source a manifest declares (roadmap <strong>D92</strong>), bound to this source's
     * name, sites, locale and secrets.
     *
     * @throws IllegalArgumentException when the manifest does not parse, or a secret it uses is not
     *                                  set
     */
    public DumJsonApiSource jsonApiSource(DumCatalogSource source) {
        DumJsonApiManifest manifest = DumJsonApiManifest.parse(source.getJsonApiManifest());
        return DumJsonApiSource.builder(manifest, jsonFetcher)
                .sourceName(source.getName())
                .providerName(DumCatalogPlugin.PROVIDER_NAME)
                .siteNames(source.getTurSNSites() == null ? List.of() : List.copyOf(source.getTurSNSites()))
                .locale(localeOf(source))
                .secrets(secretsOf(source))
                .build();
    }

    /**
     * Checks that a source's JSON API binding can run, without calling the API: the manifest parses
     * and every secret it uses is set. A source with no manifest passes.
     *
     * @throws IllegalArgumentException naming what is wrong
     */
    public static void validateJsonApi(DumCatalogSource source) {
        if (!isJsonApi(source)) {
            return;
        }
        DumJsonApiSource.builder(DumJsonApiManifest.parse(source.getJsonApiManifest()), request -> "")
                .sourceName(notBlank(source.getName()) ? source.getName() : "unnamed")
                .secrets(secretsOf(source)).build();
    }

    /** Whether this source runs a JSON API manifest rather than the sidecar or a recipe. */
    public static boolean isJsonApi(DumCatalogSource source) {
        return notBlank(source.getJsonApiManifest());
    }

    /**
     * The source's secrets, read from their JSON object form.
     *
     * @throws IllegalArgumentException when they are not a JSON object of strings
     */
    public static Map<String, String> secretsOf(DumCatalogSource source) {
        if (!notBlank(source.getJsonApiSecrets())) {
            return Map.of();
        }
        try {
            JSONObject json = new JSONObject(source.getJsonApiSecrets());
            Map<String, String> secrets = new LinkedHashMap<>();
            json.keySet().forEach(key -> secrets.put(key, json.get(key).toString()));
            return secrets;
        } catch (JSONException e) {
            throw new IllegalArgumentException("The JSON API secrets must be a JSON object of name to value");
        }
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
