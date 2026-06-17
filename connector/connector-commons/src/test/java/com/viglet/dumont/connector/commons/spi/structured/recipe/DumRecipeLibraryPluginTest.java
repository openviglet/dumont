/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */

package com.viglet.dumont.connector.commons.spi.structured.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumColumnRule;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * End-to-end test for roadmap <strong>D19</strong>: two institutions index through the
 * <em>same versioned recipe</em> with only per-source parameters, and bumping the recipe (or its
 * taxonomy) re-applies to both — never a per-source copy.
 */
class DumRecipeLibraryPluginTest {

    private static final String PROVIDER = "STRUCTURED-RECIPE";

    private static final String UFA_LISTING = "https://ufa.edu/sigaa/cursos";
    private static final String UFB_LISTING = "https://ufb.edu/sigaa/cursos";

    // Two institutions, identical SIGAA table shape, different content + URLs.
    private static final Map<String, String> PAGES = Map.of(
            UFA_LISTING, """
                    <html><body>
                    <table class="listagem"><tbody>
                      <tr><td>CC101</td><td><a href="/curso/CC101">Algoritmos</a></td>
                          <td>Computação</td><td>Presencial</td></tr>
                    </tbody></table>
                    </body></html>
                    """,
            UFB_LISTING, """
                    <html><body>
                    <table class="listagem"><tbody>
                      <tr><td>MED200</td><td><a href="/curso/MED200">Anatomia</a></td>
                          <td>Medicina</td><td>Presencial</td></tr>
                    </tbody></table>
                    </body></html>
                    """);

    @Test
    void twoInstitutionsIndexViaOneRecipe_andBumpsReapplyToBoth() {
        DumRecipeLibrary library = new DumRecipeLibrary();
        library.register(sigaaV1());
        library.register(areasV1());

        // Both sources track the recipe + taxonomy by LATEST, supplying only per-source parameters.
        DumRecipeSourceProvider provider = DumRecipeSourceProvider
                .builder(PROVIDER, library, DumHtmlFetcher.ofPages(PAGES))
                .bind(DumRecipeRef.latest("sigaa"), DumRecipeParameters.builder("ufa")
                        .site("Sample").listing(UFA_LISTING).build())
                .bind(DumRecipeRef.latest("sigaa"), DumRecipeParameters.builder("ufb")
                        .site("Sample").listing(UFB_LISTING).build())
                .build();

        CapturingContext context = new CapturingContext();
        RecipePlugin plugin = new RecipePlugin(context, provider);

        // --- v1: both institutions index at parity from the single recipe -----------------------
        plugin.crawl();

        TurSNJobItem cc101 = context.itemById("CC101");
        assertEquals("Algoritmos", cc101.getAttributes().get("title"));
        assertEquals("Computação", cc101.getAttributes().get("area"));
        assertEquals("https://ufa.edu/curso/CC101", cc101.getAttributes().get("url"));
        // areas@1 maps Computação -> canonical; the taxonomy enricher derived it.
        assertEquals("Ciências Exatas", cc101.getAttributes().get("canonicalArea"));
        // v1 recipe has no modality column.
        assertFalse(cc101.getAttributes().containsKey("modality"));

        TurSNJobItem med200 = context.itemById("MED200");
        assertEquals("Anatomia", med200.getAttributes().get("title"));
        assertEquals("Medicina", med200.getAttributes().get("area"));
        // areas@1 has no entry for Medicina -> canonicalArea omitted, never fabricated (grounding).
        assertFalse(med200.getAttributes().containsKey("canonicalArea"));

        // --- Bump the RECIPE: sigaa@2 adds a modality column. Both sources track LATEST, so the
        //     next crawl re-applies to both with no per-source edit. ------------------------------
        context.reset();
        library.register(sigaaV2());
        plugin.crawl();

        assertEquals("Presencial", context.itemById("CC101").getAttributes().get("modality"));
        assertEquals("Presencial", context.itemById("MED200").getAttributes().get("modality"));

        // --- Bump the TAXONOMY: areas@2 adds Medicina. The library-backed enricher resolves LATEST
        //     at apply time, so it re-applies live without rebuilding the chain. -------------------
        context.reset();
        library.register(areasV2());
        plugin.crawl();

        assertEquals("Ciências Exatas", context.itemById("CC101").getAttributes().get("canonicalArea"));
        assertEquals("Ciências da Saúde", context.itemById("MED200").getAttributes().get("canonicalArea"));
    }

    @Test
    void pinnedRecipeRefIsNotAffectedByABump() {
        DumRecipeLibrary library = new DumRecipeLibrary();
        library.register(sigaaV1());
        library.register(areasV1());

        // ufa pins sigaa@1 explicitly; ufb tracks latest.
        DumRecipeSourceProvider provider = DumRecipeSourceProvider
                .builder(PROVIDER, library, DumHtmlFetcher.ofPages(PAGES))
                .bind(DumRecipeRef.of("sigaa", 1), DumRecipeParameters.builder("ufa")
                        .site("Sample").listing(UFA_LISTING).build())
                .bind(DumRecipeRef.latest("sigaa"), DumRecipeParameters.builder("ufb")
                        .site("Sample").listing(UFB_LISTING).build())
                .build();

        CapturingContext context = new CapturingContext();
        RecipePlugin plugin = new RecipePlugin(context, provider);

        library.register(sigaaV2());
        plugin.crawl();

        // Pinned source stays on v1 (no modality); latest source rides the bump.
        assertFalse(context.itemById("CC101").getAttributes().containsKey("modality"));
        assertTrue(context.itemById("MED200").getAttributes().containsKey("modality"));
    }

    // --- Recipe & taxonomy versions ---------------------------------------------------------------

    private static DumStructuredSchema baseSchema() {
        return DumStructuredSchema.builder()
                .string("code")
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT)
                        .mandatory(true).build())
                .string("url")
                .string("area")
                .string("canonicalArea")
                .build();
    }

    private static DumStructuredSchema schemaWithModality() {
        return DumStructuredSchema.builder()
                .string("code")
                .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT)
                        .mandatory(true).build())
                .string("url")
                .string("area")
                .string("canonicalArea")
                .string("modality")
                .build();
    }

    private static DumTabularRecipe sigaaV1() {
        return DumTabularRecipe.builder("sigaa", 1, baseSchema())
                .rows("table.listagem > tbody > tr")
                .column("code", 0)
                .column(DumColumnRule.selector("title", "td:nth-child(2) a"))
                .column(DumColumnRule.selector("url", "td:nth-child(2) a", "abs:href"))
                .column("area", 2)
                .idField("code")
                .taxonomy(DumRecipeRef.latest("areas"), "area", "canonicalArea")
                .build();
    }

    private static DumTabularRecipe sigaaV2() {
        return DumTabularRecipe.builder("sigaa", 2, schemaWithModality())
                .rows("table.listagem > tbody > tr")
                .column("code", 0)
                .column(DumColumnRule.selector("title", "td:nth-child(2) a"))
                .column(DumColumnRule.selector("url", "td:nth-child(2) a", "abs:href"))
                .column("area", 2)
                .column("modality", 3)
                .idField("code")
                .taxonomy(DumRecipeRef.latest("areas"), "area", "canonicalArea")
                .build();
    }

    private static DumTaxonomy areasV1() {
        return DumTaxonomy.builder("areas", 1).map("Computação", "Ciências Exatas").build();
    }

    private static DumTaxonomy areasV2() {
        return DumTaxonomy.builder("areas", 2)
                .map("Computação", "Ciências Exatas")
                .map("Medicina", "Ciências da Saúde")
                .build();
    }

    // --- A plugin driven entirely by the recipe-source provider -----------------------------------

    private static final class RecipePlugin extends DumStructuredSourcePlugin {
        private final DumStructuredSourceProvider provider;

        RecipePlugin(DumConnectorContext context, DumRecipeSourceProvider provider) {
            super(context, provider.enrichmentChain());
            this.provider = provider;
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return provider;
        }
    }

    private static final class CapturingContext implements DumConnectorContext {
        private final List<DumJobItemWithSession> items = new ArrayList<>();

        void reset() {
            items.clear();
        }

        TurSNJobItem itemById(String id) {
            return items.stream()
                    .map(DumJobItemWithSession::turSNJobItem)
                    .filter(item -> id.equals(item.getAttributes().get(TurSNFieldName.ID)))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No job item with id " + id));
        }

        @Override
        public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
            return items.add(dumJobItemWithSession);
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            // no-op for the test
        }

        @Override
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source,
                String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider,
                List<String> dependenciesObjectIdList) {
            return List.of();
        }
    }
}
