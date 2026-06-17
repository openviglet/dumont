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

/**
 * Versioned, parameterized AEM content-type recipes (roadmap <strong>D27</strong>).
 *
 * <p>
 * Promotes D25's per-content-type model — a content type's schema plus its
 * {@link com.viglet.dumont.connector.aem.commons.ext.DumAemComponentExtractor} composition — to a
 * first-class, versioned artifact applied <em>by reference</em> instead of copied into a per-source
 * god-class extension. The AEM-side counterpart of D19's reusable recipe library, the endgame of
 * Track H: "one {@code course-offer} recipe &rarr; many institutions" (Insper, cursarei federals).
 *
 * <p>
 * A {@link com.viglet.dumont.connector.aem.commons.recipe.DumAemRecipe} captures the shared shape
 * (content-type name, field schema, extractor-composition factory); the only per-institution detail —
 * template names and a tiny parameter bag (component resource-type prefix, …) — lives in a
 * {@link com.viglet.dumont.connector.aem.commons.recipe.DumAemRecipeParameters}. Recipes live in the
 * {@link com.viglet.dumont.connector.aem.commons.recipe.DumAemRecipeLibrary} keyed by
 * {@link com.viglet.dumont.connector.aem.commons.recipe.DumAemRecipeRef name@version}.
 *
 * <p>
 * A recipe-driven extension stays a thin {@code DumAemExtModelJsonBase}, delegating its
 * {@code contentTypes()} / {@code templateNameAliases()} to a
 * {@link com.viglet.dumont.connector.aem.commons.recipe.DumAemRecipeContentTypes} binding. Because the
 * binding materializes from the library's current state on every call, registering a higher recipe
 * version re-applies to every institution tracking it by
 * {@link com.viglet.dumont.connector.aem.commons.recipe.DumAemRecipeRef#LATEST latest} on the next
 * extraction, while a pinned ref ({@code course-offer@1}) stays frozen.
 *
 * <p>
 * This module declares recipes without depending on the connector commons (the same boundary as
 * {@code DumAemContentTypeField}); the AEM structured-source adapter (D21/D25) translates the
 * materialized content types into the typed per-type schema each record is bound to.
 */
package com.viglet.dumont.connector.aem.commons.recipe;
