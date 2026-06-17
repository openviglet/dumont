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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.aem.commons.ext.DumAemContentType;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtModelJsonBase;

/**
 * The recipe binding an AEM extension delegates its D25 content-type wiring to (roadmap
 * <strong>D27</strong>): a {@link DumAemRecipeLibrary} plus the {@code (recipe ref, parameters)}
 * bindings for one institution.
 *
 * <p>
 * A recipe-driven extension stays a thin {@link DumAemExtModelJsonBase}: it implements only
 * {@code templateName(...)} (how to read the raw template off its model bean) and forwards
 * {@link DumAemExtModelJsonBase#contentTypes() contentTypes()} to {@link #contentTypes()} and
 * {@link DumAemExtModelJsonBase#templateNameAliases() templateNameAliases()} to
 * {@link #templateNameAliases()} here. All the schema and extraction logic lives in the shared recipes.
 *
 * <pre>{@code
 * public class InsperExt extends DumAemExtModelJsonBase<Model> {
 *     private final DumAemRecipeContentTypes<Model> recipes = DumAemRecipeContentTypes.<Model>builder(library)
 *         .bind(DumAemRecipeRef.latest("course-offer"),
 *               DumAemRecipeParameters.builder("insper").template("curso")
 *                   .param("componentBasePath", "insper/components").build())
 *         .build();
 *
 *     @Override protected List<DumAemContentType<Model>> contentTypes() { return recipes.contentTypes(); }
 *     @Override protected Map<String, String> templateNameAliases() { return recipes.templateNameAliases(); }
 *     @Override protected String templateName(Model m, DumAemObject o) { return m.getTemplate(); }
 * }
 * }</pre>
 *
 * <p>
 * Both methods resolve the recipes from the library <em>fresh on every call</em>, so registering a
 * higher recipe version (a bump) re-applies to every institution that bound the recipe by
 * {@link DumAemRecipeRef#LATEST latest} on the next extraction — without touching any extension. A
 * binding whose ref does not resolve (an unregistered/pinned-away recipe) is skipped, so a single
 * missing recipe never breaks the others.
 *
 * @param <T> the root model bean type for the model.json response
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public final class DumAemRecipeContentTypes<T> {

    /** One institution's binding of a recipe reference to its per-source parameters. */
    private record Binding(DumAemRecipeRef ref, DumAemRecipeParameters params) {
    }

    private final DumAemRecipeLibrary library;
    private final List<Binding> bindings;

    private DumAemRecipeContentTypes(DumAemRecipeLibrary library, List<Binding> bindings) {
        this.library = library;
        this.bindings = List.copyOf(bindings);
    }

    public static <T> Builder<T> builder(DumAemRecipeLibrary library) {
        return new Builder<>(library);
    }

    /**
     * The content types for this institution, materialized fresh from the library's current state — so
     * a recipe bump re-applies on the next call. Bindings whose ref does not resolve are skipped.
     */
    public List<DumAemContentType<T>> contentTypes() {
        List<DumAemContentType<T>> contentTypes = new ArrayList<>();
        for (Binding binding : bindings) {
            library.<T>materialize(binding.ref(), binding.params()).ifPresent(contentTypes::add);
        }
        return contentTypes;
    }

    /**
     * The template-name alias map for this institution: each bound recipe's per-source template names
     * mapped to that recipe's canonical content-type name. Resolved fresh, so a content-type rename in
     * a bumped recipe re-applies. A later binding wins on a duplicate template name.
     */
    public Map<String, String> templateNameAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (Binding binding : bindings) {
            library.recipe(binding.ref()).ifPresent(recipe -> binding.params().templateNames()
                    .forEach(template -> aliases.put(template, recipe.contentTypeName())));
        }
        return aliases;
    }

    public static final class Builder<T> {
        private final DumAemRecipeLibrary library;
        private final List<Binding> bindings = new ArrayList<>();

        private Builder(DumAemRecipeLibrary library) {
            if (library == null) {
                throw new IllegalArgumentException("A recipe library is required");
            }
            this.library = library;
        }

        /** Bind a recipe reference to this institution's per-source parameters. */
        public Builder<T> bind(DumAemRecipeRef ref, DumAemRecipeParameters params) {
            if (ref == null || params == null) {
                throw new IllegalArgumentException("A recipe binding needs both a ref and parameters");
            }
            bindings.add(new Binding(ref, params));
            return this;
        }

        public DumAemRecipeContentTypes<T> build() {
            return new DumAemRecipeContentTypes<>(library, bindings);
        }
    }
}
