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
import java.util.List;
import java.util.function.Function;

import com.viglet.dumont.connector.aem.commons.ext.DumAemComponentExtractor;
import com.viglet.dumont.connector.aem.commons.ext.DumAemContentType;
import com.viglet.dumont.connector.aem.commons.ext.DumAemContentTypeField;

/**
 * A versioned, parameterized <em>AEM content-type recipe</em> (roadmap <strong>D27</strong>): the
 * shared shape of a {@link DumAemContentType} — its canonical name, its field schema and its
 * {@link DumAemComponentExtractor component-extractor} composition — captured once and applied to many
 * institutions by reference.
 *
 * <p>
 * This is the AEM-side promotion of D25's per-content-type model to a first-class artifact, the way
 * D19's {@code DumTabularRecipe} did for the tabular strategy. Everything that is the <em>same</em>
 * across institutions lives in the recipe:
 * <ul>
 * <li>the {@link #contentTypeName() content-type name} (e.g. {@code course-offer}); and</li>
 * <li>the {@link #fields() field schema} (title / department / modality …), shared verbatim.</li>
 * </ul>
 * The only thing that varies per institution is supplied through {@link DumAemRecipeParameters} (the
 * component resource-type prefix, the template names). The recipe's {@link #extractorFactory()
 * extractor factory} reads those parameters and builds that institution's extractor composition, so
 * the <em>shape</em> of extraction is shared while the concrete component paths differ.
 *
 * <p>
 * {@link #materialize(DumAemRecipeParameters)} binds the recipe to one institution's parameters,
 * producing a ready {@link DumAemContentType}. It is called fresh per resolution, so a recipe bump
 * (registering a higher version in the {@link DumAemRecipeLibrary}) re-applies to every institution
 * tracking the recipe by {@link DumAemRecipeRef#LATEST_VERSION latest} on the next materialization.
 *
 * @param <T> the root model bean type for the model.json response
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public final class DumAemRecipe<T> {

    private final String name;
    private final int version;
    private final String contentTypeName;
    private final List<DumAemContentTypeField> fields;
    private final Function<DumAemRecipeParameters, List<DumAemComponentExtractor<T>>> extractorFactory;

    private DumAemRecipe(Builder<T> builder) {
        this.name = builder.name;
        this.version = builder.version;
        this.contentTypeName = builder.contentTypeName;
        this.fields = List.copyOf(builder.fields);
        this.extractorFactory = builder.extractorFactory;
    }

    public static <T> Builder<T> builder(String name, int version, String contentTypeName) {
        return new Builder<>(name, version, contentTypeName);
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    /** This recipe's identity as a pinned {@link DumAemRecipeRef} ({@code name@version}). */
    public DumAemRecipeRef ref() {
        return DumAemRecipeRef.of(name, version);
    }

    /** The canonical content-type name a resolved template maps to (shared across institutions). */
    public String contentTypeName() {
        return contentTypeName;
    }

    /** The field schema this recipe publishes — shared verbatim across institutions, in order. */
    public List<DumAemContentTypeField> fields() {
        return fields;
    }

    /** The factory that builds an institution's extractor composition from its per-source parameters. */
    public Function<DumAemRecipeParameters, List<DumAemComponentExtractor<T>>> extractorFactory() {
        return extractorFactory;
    }

    /**
     * Bind this recipe to one institution's {@code params}, producing a ready {@link DumAemContentType}:
     * the recipe's shared schema plus the per-source extractor composition the factory builds. Called
     * fresh per resolution, so the content type always reflects the recipe version currently registered.
     */
    public DumAemContentType<T> materialize(DumAemRecipeParameters params) {
        DumAemContentType.Builder<T> contentType = DumAemContentType.builder(contentTypeName);
        fields.forEach(contentType::field);
        for (DumAemComponentExtractor<T> extractor : extractorFactory.apply(params)) {
            contentType.extractor(extractor);
        }
        return contentType.build();
    }

    public static final class Builder<T> {
        private final String name;
        private final int version;
        private final String contentTypeName;
        private final List<DumAemContentTypeField> fields = new ArrayList<>();
        private Function<DumAemRecipeParameters, List<DumAemComponentExtractor<T>>> extractorFactory =
                params -> List.of();

        private Builder(String name, int version, String contentTypeName) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("AEM recipe name is required");
            }
            if (version <= 0) {
                throw new IllegalArgumentException("AEM recipe version must be positive: " + version);
            }
            if (contentTypeName == null || contentTypeName.isBlank()) {
                throw new IllegalArgumentException("AEM recipe content-type name is required");
            }
            this.name = name.trim();
            this.version = version;
            this.contentTypeName = contentTypeName.trim();
        }

        public Builder<T> field(DumAemContentTypeField field) {
            this.fields.add(field);
            return this;
        }

        public Builder<T> fields(DumAemContentTypeField... fields) {
            for (DumAemContentTypeField field : fields) {
                this.fields.add(field);
            }
            return this;
        }

        /**
         * The factory that builds the per-institution extractor composition from its parameters. This
         * is where the shared <em>shape</em> meets the per-source detail (component resource-type
         * prefix, etc.).
         */
        public Builder<T> extractors(
                Function<DumAemRecipeParameters, List<DumAemComponentExtractor<T>>> extractorFactory) {
            this.extractorFactory = extractorFactory;
            return this;
        }

        public DumAemRecipe<T> build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException(
                        "AEM recipe '" + name + "' must declare at least one field");
            }
            if (extractorFactory == null) {
                throw new IllegalStateException(
                        "AEM recipe '" + name + "' must declare an extractor factory");
            }
            return new DumAemRecipe<>(this);
        }
    }
}
