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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.commons.ext.DumAemContentType;
import com.viglet.dumont.connector.aem.commons.ext.DumAemContentTypeField;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtModelJsonBase;

/**
 * End-to-end test for roadmap <strong>D27</strong>: two institutions index a <em>shared content
 * type</em> through the <em>same versioned recipe</em> with only per-source parameters, and bumping
 * the recipe re-applies to both — never a per-source god-class copy. The AEM-side analogue of the D19
 * {@code DumRecipeLibraryPluginTest}.
 */
@DisplayName("DumAemRecipeLibrary — shared versioned AEM content-type recipes (D27)")
class DumAemRecipeLibraryTest {

    private static final String RECIPE = "course-offer";

    @Test
    @DisplayName("Two institutions index a shared content type via one recipe; a bump re-applies to both")
    void twoInstitutionsShareOneRecipe_andBumpReappliesToBoth() {
        DumAemRecipeLibrary library = new DumAemRecipeLibrary();
        library.register(courseOfferV1());

        // Both institutions track the recipe by LATEST, supplying only per-source parameters: the
        // template name that routes to the content type and a tiny parameter bag (the department).
        RecipeExtension insper = institution(library, DumAemRecipeRef.latest(RECIPE),
                DumAemRecipeParameters.builder("insper").template("curso")
                        .param("dept", "Engenharia").build());
        RecipeExtension federal = institution(library, DumAemRecipeRef.latest(RECIPE),
                DumAemRecipeParameters.builder("federal").template("graduacao")
                        .param("dept", "Medicina").build());

        // Both institutions resolve the SAME shared content type + schema from the one recipe.
        assertEquals(List.of("course-offer"), contentTypeNames(insper));
        assertEquals(List.of("course-offer"), contentTypeNames(federal));
        assertEquals(List.of("title", "department"), fieldNames(insper, "course-offer"));
        assertEquals(fieldNames(insper, "course-offer"), fieldNames(federal, "course-offer"));

        // --- v1: each institution extracts via the shared recipe, differing only by its parameters ---
        DumAemAttrMap insperV1 = insper.run("curso");
        assertEquals("curso-course", insperV1.get("title").getFirst());
        assertEquals("Engenharia", insperV1.get("department").getFirst());
        assertFalse(insperV1.containsKey("modality"), "v1 recipe has no modality field");

        DumAemAttrMap federalV1 = federal.run("graduacao");
        assertEquals("graduacao-course", federalV1.get("title").getFirst());
        assertEquals("Medicina", federalV1.get("department").getFirst());

        // --- Bump the RECIPE: course-offer@2 adds a modality field + extractor. Both institutions
        //     track LATEST, so the next extraction re-applies to both with no per-source edit. -------
        library.register(courseOfferV2());

        assertEquals(List.of("title", "department", "modality"), fieldNames(insper, "course-offer"));
        assertEquals(List.of("title", "department", "modality"), fieldNames(federal, "course-offer"));

        assertEquals("Presencial", insper.run("curso").get("modality").getFirst());
        assertEquals("Presencial", federal.run("graduacao").get("modality").getFirst());
    }

    @Test
    @DisplayName("A pinned recipe ref is frozen on a bump; a latest ref rides it")
    void pinnedRecipeRefIsFrozenOnBump() {
        DumAemRecipeLibrary library = new DumAemRecipeLibrary();
        library.register(courseOfferV1());

        // insper pins course-offer@1 explicitly; federal tracks latest.
        RecipeExtension insper = institution(library, DumAemRecipeRef.of(RECIPE, 1),
                DumAemRecipeParameters.builder("insper").template("curso")
                        .param("dept", "Engenharia").build());
        RecipeExtension federal = institution(library, DumAemRecipeRef.latest(RECIPE),
                DumAemRecipeParameters.builder("federal").template("graduacao")
                        .param("dept", "Medicina").build());

        library.register(courseOfferV2());

        // Pinned institution stays on v1 (no modality); latest institution rides the bump.
        assertFalse(insper.run("curso").containsKey("modality"));
        assertTrue(federal.run("graduacao").containsKey("modality"));
    }

    @Test
    @DisplayName("A binding whose recipe is unregistered is skipped, never breaking the rest")
    void unregisteredRecipeBindingIsSkipped() {
        DumAemRecipeLibrary library = new DumAemRecipeLibrary();
        library.register(courseOfferV1());

        DumAemRecipeContentTypes<String> recipes = DumAemRecipeContentTypes.<String>builder(library)
                .bind(DumAemRecipeRef.latest(RECIPE), DumAemRecipeParameters.builder("insper")
                        .template("curso").param("dept", "Engenharia").build())
                .bind(DumAemRecipeRef.latest("not-registered"), DumAemRecipeParameters.builder("insper")
                        .template("evento").build())
                .build();

        // Only the resolvable recipe materializes; the missing one is skipped (no exception).
        assertEquals(List.of("course-offer"),
                recipes.contentTypes().stream().map(DumAemContentType::name).toList());
        assertEquals(Map.of("curso", "course-offer"), recipes.templateNameAliases());
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private static RecipeExtension institution(DumAemRecipeLibrary library, DumAemRecipeRef ref,
            DumAemRecipeParameters params) {
        return new RecipeExtension(DumAemRecipeContentTypes.<String>builder(library)
                .bind(ref, params).build());
    }

    private static List<String> contentTypeNames(RecipeExtension extension) {
        return extension.declaredContentTypes().stream().map(DumAemContentType::name).toList();
    }

    private static List<String> fieldNames(RecipeExtension extension, String contentType) {
        return extension.declaredContentTypes().stream()
                .filter(type -> type.name().equals(contentType))
                .findFirst().orElseThrow()
                .fields().stream().map(DumAemContentTypeField::name).toList();
    }

    // --- Recipe versions: the shared shape, parameterized per institution -------------------------

    private static DumAemRecipe<String> courseOfferV1() {
        return DumAemRecipe.<String>builder(RECIPE, 1, "course-offer")
                .fields(DumAemContentTypeField.text("title").asMandatory(),
                        DumAemContentTypeField.facet("department"))
                .extractors(params -> List.of((model, query, aemObject, attrs) -> {
                    attrs.set("title", model + "-course");
                    params.param("dept").ifPresent(dept -> attrs.set("department", dept));
                }))
                .build();
    }

    private static DumAemRecipe<String> courseOfferV2() {
        return DumAemRecipe.<String>builder(RECIPE, 2, "course-offer")
                .fields(DumAemContentTypeField.text("title").asMandatory(),
                        DumAemContentTypeField.facet("department"),
                        DumAemContentTypeField.string("modality"))
                .extractors(params -> List.of((model, query, aemObject, attrs) -> {
                    attrs.set("title", model + "-course");
                    params.param("dept").ifPresent(dept -> attrs.set("department", dept));
                    attrs.set("modality", params.param("modality", "Presencial"));
                }))
                .build();
    }

    /**
     * A thin recipe-driven extension: it implements only how to read the raw template off its model
     * bean (here the {@code String} model <em>is</em> the template name) and forwards content-type
     * wiring to the {@link DumAemRecipeContentTypes} binding — all schema/extraction lives in recipes.
     */
    private static final class RecipeExtension extends DumAemExtModelJsonBase<String> {
        private final DumAemRecipeContentTypes<String> recipes;

        RecipeExtension(DumAemRecipeContentTypes<String> recipes) {
            this.recipes = recipes;
        }

        @Override
        protected Class<String> getModelClass() {
            return String.class;
        }

        @Override
        protected String templateName(String model, DumAemObject aemObject) {
            return model;
        }

        @Override
        protected List<DumAemContentType<String>> contentTypes() {
            return recipes.contentTypes();
        }

        @Override
        protected Map<String, String> templateNameAliases() {
            return recipes.templateNameAliases();
        }

        DumAemAttrMap run(String template) {
            DumAemAttrMap attrs = new DumAemAttrMap();
            extractAttributes(template, null, null, attrs);
            return attrs;
        }
    }
}
