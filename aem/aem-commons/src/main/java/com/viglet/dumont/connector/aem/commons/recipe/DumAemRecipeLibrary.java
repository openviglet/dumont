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

package com.viglet.dumont.connector.aem.commons.recipe;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import com.viglet.dumont.connector.aem.commons.ext.DumAemContentType;

/**
 * The versioned store of reusable {@link DumAemRecipe AEM content-type recipes} (roadmap
 * <strong>D27</strong>) — the registry that makes recipes "applied by reference instead of copied per
 * institution". The AEM-side counterpart of D19's {@code DumRecipeLibrary}.
 *
 * <p>
 * Each recipe name holds an ordered set of versions. A {@link DumAemRecipeRef} resolves either a
 * pinned version ({@code course-offer@3}) or, when {@link DumAemRecipeRef#isLatest() latest}, the
 * highest registered version. {@link #materialize materialize(ref, params)} turns the stored recipe
 * into a ready {@link DumAemContentType} for one institution — the recipe's shared schema plus that
 * institution's per-source parameters. Re-resolving after a bump yields the new version, which is why
 * registering a higher version re-applies to every institution tracking the recipe by
 * {@link DumAemRecipeRef#LATEST latest}.
 *
 * <p>
 * The store is thread-safe; registering a new version is a single put, so a bump is atomic from a
 * reader's point of view.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public final class DumAemRecipeLibrary {

    private final Map<String, NavigableMap<Integer, DumAemRecipe<?>>> recipes =
            new ConcurrentHashMap<>();

    /** Register (or bump) a recipe version. A later version of the same name supersedes it for {@code latest}. */
    public DumAemRecipeLibrary register(DumAemRecipe<?> recipe) {
        recipes.computeIfAbsent(recipe.name(), n -> new ConcurrentSkipListMap<>())
                .put(recipe.version(), recipe);
        return this;
    }

    /**
     * Resolve a recipe by reference (pinned version, or the newest when
     * {@link DumAemRecipeRef#isLatest()}). The cast is safe by construction: a recipe is bound to one
     * model bean type per name, and callers resolve it under the same {@code <T>} the extension declares.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<DumAemRecipe<T>> recipe(DumAemRecipeRef ref) {
        NavigableMap<Integer, DumAemRecipe<?>> versions = recipes.get(ref.name());
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        DumAemRecipe<?> resolved =
                ref.isLatest() ? versions.lastEntry().getValue() : versions.get(ref.version());
        return Optional.ofNullable((DumAemRecipe<T>) resolved);
    }

    /**
     * Materialize the {@link DumAemContentType} for {@code params} from the recipe {@code ref} resolves
     * to. Returns {@link Optional#empty()} when no such recipe is registered.
     */
    public <T> Optional<DumAemContentType<T>> materialize(DumAemRecipeRef ref,
            DumAemRecipeParameters params) {
        return this.<T>recipe(ref).map(recipe -> recipe.materialize(params));
    }
}
