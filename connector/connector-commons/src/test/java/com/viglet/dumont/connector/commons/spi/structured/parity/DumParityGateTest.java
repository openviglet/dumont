/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */

package com.viglet.dumont.connector.commons.spi.structured.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredField;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeParameters;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeRef;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTabularRecipe;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumColumnRule;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * The roadmap <strong>D11</strong> parity gate: a native declarative strategy is only allowed to
 * replace the sidecar (Python) run for a source once it reproduces the same population (record count
 * within tolerance) and the same public URLs (coverage). These tests model the SIGAA-federal leg —
 * a catalog-style baseline vs. a recipe-materialized native candidate over the same listing.
 */
class DumParityGateTest {

    private static final String PROVIDER = "CATALOG-BRIDGE";
    private static final String LISTING = "https://ufmg.br/sigaa/cursos";

    private static final DumStructuredSchema SCHEMA = DumStructuredSchema.builder()
            .string("code")
            .field(DumStructuredField.builder("title").type(TurSEFieldType.TEXT).mandatory(true)
                    .build())
            .field(DumStructuredField.builder("url").type(TurSEFieldType.STRING).mandatory(true)
                    .build())
            .build();

    // The incumbent sidecar run: three courses with their public URLs (what the Python engine emits).
    private final DumStructuredSource sidecar = new InMemorySource("ufmg", Map.of(
            "CC101", "https://ufmg.br/curso/CC101",
            "MED200", "https://ufmg.br/curso/MED200",
            "DIR300", "https://ufmg.br/curso/DIR300"));

    @Test
    void nativeStrategyAtParityPassesTheGate() {
        DumStructuredSource nativeSource = recipeSource("""
                <html><body><table class="listagem"><tbody>
                  <tr><td>CC101</td><td><a href="/curso/CC101">Algoritmos</a></td></tr>
                  <tr><td>MED200</td><td><a href="/curso/MED200">Anatomia</a></td></tr>
                  <tr><td>DIR300</td><td><a href="/curso/DIR300">Direito Civil</a></td></tr>
                </tbody></table></body></html>
                """);

        DumParityReport report =
                DumParityGate.compare(sidecar, nativeSource, DumParityCriteria.defaults());

        assertTrue(report.passed(), () -> "expected parity, reasons: " + report.reasons());
        assertEquals(3, report.baselineCount());
        assertEquals(3, report.candidateCount());
        assertEquals(3, report.matchedUrlCount());
        assertEquals(1.0, report.urlCoverage());
        assertTrue(report.missingUrls().isEmpty());
        assertTrue(report.extraUrls().isEmpty());
    }

    @Test
    void nativeStrategyMissingCoursesFailsOnCountAndCoverage() {
        // The recipe selectors silently stopped matching one row's link — a dropped course.
        DumStructuredSource degraded = recipeSource("""
                <html><body><table class="listagem"><tbody>
                  <tr><td>CC101</td><td><a href="/curso/CC101">Algoritmos</a></td></tr>
                  <tr><td>MED200</td><td><a href="/curso/MED200">Anatomia</a></td></tr>
                </tbody></table></body></html>
                """);

        DumParityReport report =
                DumParityGate.compare(sidecar, degraded, DumParityCriteria.defaults());

        assertFalse(report.passed());
        assertFalse(report.countWithinTolerance());
        assertFalse(report.urlCoverageMet());
        assertEquals(2, report.candidateCount());
        assertEquals(List.of("https://ufmg.br/curso/DIR300"), report.missingUrls());
        assertEquals(2, report.reasons().size());
    }

    @Test
    void extraUrlsAreReportedButLooseGateStillPasses() {
        // Native finds one more course than the sidecar; a tolerant gate accepts the small growth.
        DumStructuredSource richer = recipeSource("""
                <html><body><table class="listagem"><tbody>
                  <tr><td>CC101</td><td><a href="/curso/CC101">Algoritmos</a></td></tr>
                  <tr><td>MED200</td><td><a href="/curso/MED200">Anatomia</a></td></tr>
                  <tr><td>DIR300</td><td><a href="/curso/DIR300">Direito Civil</a></td></tr>
                  <tr><td>ENG400</td><td><a href="/curso/ENG400">Engenharia</a></td></tr>
                </tbody></table></body></html>
                """);

        DumParityCriteria loose =
                DumParityCriteria.defaults().withCountTolerance(0.5).withMinUrlCoverage(1.0);
        DumParityReport report = DumParityGate.compare(sidecar, richer, loose);

        assertTrue(report.passed(), () -> "reasons: " + report.reasons());
        assertEquals(1.0, report.urlCoverage());
        assertEquals(List.of("https://ufmg.br/curso/ENG400"), report.extraUrls());
    }

    private DumStructuredSource recipeSource(String listingHtml) {
        DumRecipeLibrary library = new DumRecipeLibrary();
        library.register(DumTabularRecipe.builder("sigaa-federal", 1, SCHEMA)
                .rows("table.listagem > tbody > tr")
                .column("code", 0)
                .column(DumColumnRule.selector("title", "td:nth-child(2) a"))
                .column(DumColumnRule.selector("url", "td:nth-child(2) a", "abs:href"))
                .idField("code")
                .build());
        DumRecipeParameters params = DumRecipeParameters.builder("ufmg").listing(LISTING).build();
        return library.materialize(DumRecipeRef.latest("sigaa-federal"), params, PROVIDER,
                DumHtmlFetcher.ofPages(Map.of(LISTING, listingHtml))).orElseThrow();
    }

    /** A minimal structured source standing in for the catalog sidecar / Python run. */
    private static final class InMemorySource implements DumStructuredSource {
        private final String name;
        private final Map<String, String> urlsById;

        InMemorySource(String name, Map<String, String> urlsById) {
            this.name = name;
            this.urlsById = new LinkedHashMap<>(urlsById);
        }

        @Override
        public String getSourceName() {
            return name;
        }

        @Override
        public String getProviderName() {
            return PROVIDER;
        }

        @Override
        public Collection<String> getSiteNames() {
            return List.of("Sample");
        }

        @Override
        public DumStructuredSchema schema() {
            return SCHEMA;
        }

        @Override
        public List<String> discover() {
            return new ArrayList<>(urlsById.keySet());
        }

        @Override
        public Optional<DumStructuredRecord> extract(String contentId) {
            String url = urlsById.get(contentId);
            if (url == null) {
                return Optional.empty();
            }
            return Optional.of(DumStructuredRecord.builder(contentId, SCHEMA)
                    .set("title", "Course " + contentId).set("url", url).build());
        }
    }
}
