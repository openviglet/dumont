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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnricher;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;

/**
 * A {@link DumStructuredSourceProvider} whose sources are <em>bindings</em> to versioned recipes in a
 * {@link DumRecipeLibrary} (roadmap <strong>D19</strong>): each managed source is a recipe reference
 * plus that source's {@link DumRecipeParameters}.
 *
 * <p>
 * Because {@link #resolve(String)} materializes the source on demand from the library's current
 * state, bumping a recipe (registering a new version under a name a binding tracks via
 * {@link DumRecipeRef#LATEST}) re-applies to every source bound to it on the next run — no per-source
 * edit. {@link #enrichmentChain()} aggregates the taxonomy enrichers of every bound recipe into one
 * library-backed chain to hand to the {@code DumStructuredSourcePlugin}; taxonomy bumps then re-apply
 * live (the enrichers resolve their taxonomy at apply time).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumRecipeSourceProvider implements DumStructuredSourceProvider {

    /** One managed source: which recipe it tracks and the per-source parameters to apply. */
    public record Binding(DumRecipeRef recipe, DumRecipeParameters parameters) {
        public Binding {
            if (recipe == null) {
                throw new IllegalArgumentException("A binding needs a recipe ref");
            }
            if (parameters == null) {
                throw new IllegalArgumentException("A binding needs per-source parameters");
            }
        }
    }

    private final String providerName;
    private final DumRecipeLibrary library;
    private final DumHtmlFetcher fetcher;
    private final Map<String, Binding> bindings;

    private DumRecipeSourceProvider(Builder builder) {
        this.providerName = builder.providerName;
        this.library = builder.library;
        this.fetcher = builder.fetcher;
        this.bindings = new LinkedHashMap<>(builder.bindings);
    }

    public static Builder builder(String providerName, DumRecipeLibrary library,
            DumHtmlFetcher fetcher) {
        return new Builder(providerName, library, fetcher);
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public Collection<String> sourceNames() {
        return new ArrayList<>(bindings.keySet());
    }

    @Override
    public Optional<DumStructuredSource> resolve(String sourceName) {
        Binding binding = bindings.get(sourceName);
        if (binding == null) {
            return Optional.empty();
        }
        return library.materialize(binding.recipe(), binding.parameters(), providerName, fetcher);
    }

    /**
     * The enrichment chain to hand the owning plugin: the union of every bound recipe's taxonomy
     * enrichers, de-duplicated by enricher name so two sources sharing a recipe contribute it once.
     */
    public DumEnrichmentChain enrichmentChain() {
        Set<String> seen = new LinkedHashSet<>();
        List<DumEnricher> enrichers = new ArrayList<>();
        for (Binding binding : bindings.values()) {
            DumEnrichmentChain chain = library.enrichmentChain(binding.recipe());
            chain.enrichers().stream()
                    .filter(enricher -> seen.add(enricher.name()))
                    .forEach(enrichers::add);
        }
        return DumEnrichmentChain.of(enrichers);
    }

    public static final class Builder {
        private final String providerName;
        private final DumRecipeLibrary library;
        private final DumHtmlFetcher fetcher;
        private final Map<String, Binding> bindings = new LinkedHashMap<>();

        private Builder(String providerName, DumRecipeLibrary library, DumHtmlFetcher fetcher) {
            if (providerName == null || providerName.isBlank()) {
                throw new IllegalArgumentException("A provider name is required");
            }
            if (library == null) {
                throw new IllegalArgumentException("A recipe library is required");
            }
            if (fetcher == null) {
                throw new IllegalArgumentException("A fetcher is required");
            }
            this.providerName = providerName;
            this.library = library;
            this.fetcher = fetcher;
        }

        /** Bind a source to a recipe with its per-source parameters. */
        public Builder bind(DumRecipeRef recipe, DumRecipeParameters parameters) {
            bindings.put(parameters.sourceName(), new Binding(recipe, parameters));
            return this;
        }

        public DumRecipeSourceProvider build() {
            return new DumRecipeSourceProvider(this);
        }
    }
}
