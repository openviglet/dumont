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
import java.util.Optional;

/**
 * The <em>per-institution</em> parameters an AEM recipe is materialized against (roadmap
 * <strong>D27</strong>).
 *
 * <p>
 * A {@link DumAemRecipe} captures everything shared across institutions — the content-type name, its
 * field schema and the shape of its component-extractor composition. What changes per institution is
 * small and lives here:
 * <ul>
 * <li>the raw AEM {@link #templateNames() template names} that route to this recipe's content type
 * for this institution (e.g. {@code curso} at Insper vs {@code graduacao} at a federal); and</li>
 * <li>a tiny bag of named {@link #param(String) parameters} (e.g. the component resource-type prefix
 * {@code insper/components} vs {@code cursarei/components}) the recipe's extractor factory reads to
 * build the institution's extractors.</li>
 * </ul>
 *
 * <p>
 * The leverage D27 promises is exactly this split: many institutions indexing the same content type
 * become one recipe plus one of these tiny parameter sets each — not many near-identical god-class
 * extensions. The AEM-side counterpart of D19's {@code DumRecipeParameters}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public record DumAemRecipeParameters(String sourceName, List<String> templateNames,
        Map<String, String> params) {

    public DumAemRecipeParameters {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("AEM recipe parameters need a source name");
        }
        templateNames = templateNames == null ? List.of() : List.copyOf(templateNames);
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** The value of the named parameter, when present and non-blank. */
    public Optional<String> param(String key) {
        return Optional.ofNullable(params.get(key)).filter(value -> !value.isBlank());
    }

    /** The value of the named parameter, or {@code defaultValue} when absent/blank. */
    public String param(String key, String defaultValue) {
        return param(key).orElse(defaultValue);
    }

    public static Builder builder(String sourceName) {
        return new Builder(sourceName);
    }

    public static final class Builder {
        private final String sourceName;
        private final List<String> templateNames = new ArrayList<>();
        private final Map<String, String> params = new LinkedHashMap<>();

        private Builder(String sourceName) {
            this.sourceName = sourceName;
        }

        /** A raw AEM template name that routes to this recipe's content type for this source. */
        public Builder template(String templateName) {
            this.templateNames.add(templateName);
            return this;
        }

        public Builder templates(Iterable<String> templateNames) {
            templateNames.forEach(this.templateNames::add);
            return this;
        }

        /** A named per-source parameter the recipe's extractor factory reads. */
        public Builder param(String key, String value) {
            this.params.put(key, value);
            return this;
        }

        public DumAemRecipeParameters build() {
            return new DumAemRecipeParameters(sourceName, templateNames, params);
        }
    }
}
