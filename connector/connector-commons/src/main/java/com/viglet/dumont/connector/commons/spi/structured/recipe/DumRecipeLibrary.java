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
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;

/**
 * The versioned store of reusable {@link DumTabularRecipe recipes} and {@link DumTaxonomy
 * taxonomies} (roadmap <strong>D19</strong>) — the registry that makes recipes "applied by
 * reference instead of copied per source".
 *
 * <p>
 * Each artifact name holds an ordered set of versions. A {@link DumRecipeRef} resolves either a
 * pinned version ({@code sigaa@3}) or, when {@link DumRecipeRef#isLatest() latest}, the highest
 * registered version. Two operations turn a stored recipe into running config:
 *
 * <ul>
 * <li>{@link #materialize materialize(ref, params, provider, fetcher)} builds the
 * {@link DumStructuredSource} for one source — the recipe's schema/selectors plus that source's
 * per-source parameters. Re-resolving after a bump yields the new version.</li>
 * <li>{@link #enrichmentChain(DumRecipeRef)} builds the recipe's taxonomy enrichers as
 * <em>library-backed</em> {@link DumTaxonomyEnricher}s, so a taxonomy bump re-applies live without
 * rebuilding the chain.</li>
 * </ul>
 *
 * <p>
 * The store is thread-safe; registering a new version is a single put, so a bump is atomic from a
 * reader's point of view.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumRecipeLibrary {

    private final Map<String, NavigableMap<Integer, DumTabularRecipe>> recipes =
            new ConcurrentHashMap<>();
    private final Map<String, NavigableMap<Integer, DumTaxonomy>> taxonomies =
            new ConcurrentHashMap<>();

    /** Register (or bump) a recipe version. A later version of the same name supersedes it for {@code latest}. */
    public DumRecipeLibrary register(DumTabularRecipe recipe) {
        recipes.computeIfAbsent(recipe.name(), n -> new ConcurrentSkipListMap<>())
                .put(recipe.version(), recipe);
        return this;
    }

    /** Register (or bump) a taxonomy version. */
    public DumRecipeLibrary register(DumTaxonomy taxonomy) {
        taxonomies.computeIfAbsent(taxonomy.name(), n -> new ConcurrentSkipListMap<>())
                .put(taxonomy.version(), taxonomy);
        return this;
    }

    /** Resolve a recipe by reference (pinned version, or the newest when {@link DumRecipeRef#isLatest()}). */
    public Optional<DumTabularRecipe> recipe(DumRecipeRef ref) {
        return resolve(recipes.get(ref.name()), ref);
    }

    /** Resolve a taxonomy by reference (pinned version, or the newest when {@link DumRecipeRef#isLatest()}). */
    public Optional<DumTaxonomy> taxonomy(DumRecipeRef ref) {
        return resolve(taxonomies.get(ref.name()), ref);
    }

    private static <T> Optional<T> resolve(NavigableMap<Integer, T> versions, DumRecipeRef ref) {
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        if (ref.isLatest()) {
            return Optional.of(versions.lastEntry().getValue());
        }
        return Optional.ofNullable(versions.get(ref.version()));
    }

    /**
     * Materialize the source for {@code params} from the recipe {@code ref} resolves to. Returns
     * {@link Optional#empty()} when no such recipe is registered.
     */
    public Optional<DumStructuredSource> materialize(DumRecipeRef ref, DumRecipeParameters params,
            String providerName, DumHtmlFetcher fetcher) {
        return recipe(ref).map(recipe ->
                new DumRecipeTabularSource(providerName, recipe, params, fetcher));
    }

    /**
     * Build the enrichment chain for the recipe {@code ref} resolves to: one library-backed
     * {@link DumTaxonomyEnricher} per declared {@link DumTaxonomyApplication}. The enrichers resolve
     * their taxonomy from this library at apply time, so a taxonomy bump re-applies without rebuilding
     * the chain. An unknown recipe yields an {@link DumEnrichmentChain#empty() empty} chain.
     */
    public DumEnrichmentChain enrichmentChain(DumRecipeRef ref) {
        return recipe(ref)
                .map(recipe -> chainOf(recipe.taxonomyApplications()))
                .orElseGet(DumEnrichmentChain::empty);
    }

    private DumEnrichmentChain chainOf(List<DumTaxonomyApplication> applications) {
        List<DumEnricher> enrichers = new ArrayList<>();
        for (DumTaxonomyApplication application : applications) {
            enrichers.add(new DumTaxonomyEnricher(this, application));
        }
        return DumEnrichmentChain.of(enrichers);
    }
}
