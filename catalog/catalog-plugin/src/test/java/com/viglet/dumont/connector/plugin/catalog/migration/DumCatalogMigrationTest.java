/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */

package com.viglet.dumont.connector.plugin.catalog.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.parity.DumParityCriteria;
import com.viglet.dumont.connector.commons.spi.structured.parity.DumParityReport;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.dumont.connector.plugin.catalog.fetch.DumCatalogFetcher;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;
import com.viglet.dumont.connector.plugin.catalog.source.DumCatalogSourceProvider;

/**
 * Roadmap <strong>D11</strong>: the SIGAA-federal leva flips from the sidecar to the shared native
 * recipe only after the per-source parity gate passes (count + URL coverage vs the Python run), and
 * the gate blocks the flip when the native crawl loses courses.
 */
class DumCatalogMigrationTest {

    private static final String LISTING = "https://ufmg.br/sigaa/cursos";

    // Three federal courses, the public URLs the Python engine already emits.
    private static final String SIDECAR_JSON = """
            [
              { "id": "admin", "title": "Administração", "url": "https://ufmg.br/curso/admin" },
              { "id": "med",   "title": "Medicina",      "url": "https://ufmg.br/curso/med" },
              { "id": "dir",   "title": "Direito",       "url": "https://ufmg.br/curso/dir" }
            ]
            """;

    // The same three courses, scraped natively from the SIGAA listagem table.
    private static final String SIGAA_FULL = """
            <html><body><table class="listagem"><tbody>
              <tr><td class="nome"><a href="/curso/admin">Administração</a></td>
                  <td class="nivel">Graduação</td><td class="modalidade">Presencial</td></tr>
              <tr><td class="nome"><a href="/curso/med">Medicina</a></td>
                  <td class="nivel">Graduação</td><td class="modalidade">Presencial</td></tr>
              <tr><td class="nome"><a href="/curso/dir">Direito</a></td>
                  <td class="nivel">Graduação</td><td class="modalidade">Presencial</td></tr>
            </tbody></table></body></html>
            """;

    // A degraded crawl: the listagem pagination dropped a row, so a course is lost entirely.
    private static final String SIGAA_DEGRADED = """
            <html><body><table class="listagem"><tbody>
              <tr><td class="nome"><a href="/curso/admin">Administração</a></td>
                  <td class="nivel">Graduação</td><td class="modalidade">Presencial</td></tr>
              <tr><td class="nome"><a href="/curso/med">Medicina</a></td>
                  <td class="nivel">Graduação</td><td class="modalidade">Presencial</td></tr>
            </tbody></table></body></html>
            """;

    private DumCatalogSource federalSource() {
        DumCatalogSource source = new DumCatalogSource();
        source.setId("id-ufmg");
        source.setName("ufmg");
        source.setCatalogUrl("https://engine/ufmg.json");
        source.setLocale("pt_BR");
        source.setTurSNSites(Set.of("cursarei"));
        source.setLeva("sigaa-federals");
        source.setNativeRecipe(DumSigaaFederalRecipe.RECIPE_NAME);
        source.setNativeListingUrl(LISTING);
        // Not yet flipped — still on the sidecar until the gate passes.
        source.setMigrationState(DumCatalogMigrationState.SIDECAR);
        return source;
    }

    private DumCatalogSourceProvider provider(String listingHtml) {
        DumRecipeLibrary library = new DumRecipeLibrary().register(DumSigaaFederalRecipe.recipe());
        DumCatalogFetcher catalogFetcher = s -> SIDECAR_JSON;
        DumHtmlFetcher htmlFetcher = DumHtmlFetcher.ofPages(Map.of(LISTING, listingHtml));
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        return new DumCatalogSourceProvider(repository, catalogFetcher, library, htmlFetcher);
    }

    @Test
    void nativeRecipeAtParityFlipsTheSourceToNative() {
        DumCatalogSource source = federalSource();
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        when(repository.findById("id-ufmg")).thenReturn(Optional.of(source));
        when(repository.save(any(DumCatalogSource.class))).thenAnswer(call -> call.getArgument(0));

        DumCatalogParityService service =
                new DumCatalogParityService(repository, provider(SIGAA_FULL));

        DumParityReport report = service.migrateToNative("id-ufmg", DumParityCriteria.defaults());

        assertThat(report.passed()).as("reasons: %s", report.reasons()).isTrue();
        assertThat(report.baselineCount()).isEqualTo(3);
        assertThat(report.candidateCount()).isEqualTo(3);
        assertThat(report.urlCoverage()).isEqualTo(1.0);
        assertThat(source.getMigrationState()).isEqualTo(DumCatalogMigrationState.NATIVE);
        verify(repository).save(source);
    }

    @Test
    void degradedNativeCrawlIsBlockedAndStaysOnSidecar() {
        DumCatalogSource source = federalSource();
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        when(repository.findById("id-ufmg")).thenReturn(Optional.of(source));

        DumCatalogParityService service =
                new DumCatalogParityService(repository, provider(SIGAA_DEGRADED));

        DumParityReport report = service.migrateToNative("id-ufmg", DumParityCriteria.defaults());

        assertThat(report.passed()).isFalse();
        assertThat(report.candidateCount()).isEqualTo(2);
        assertThat(report.missingUrls()).containsExactly("https://ufmg.br/curso/dir");
        assertThat(source.getMigrationState()).isEqualTo(DumCatalogMigrationState.SIDECAR);
        verify(repository, never()).save(any(DumCatalogSource.class));
    }

    @Test
    void flippedSourceResolvesToTheNativeStrategy() {
        DumCatalogSource source = federalSource();
        source.setMigrationState(DumCatalogMigrationState.NATIVE);

        DumCatalogSourceProvider provider = provider(SIGAA_FULL);

        // The flipped source crawls the SIGAA table; the sidecar JSON is no longer consulted.
        assertThat(provider.isNative(source)).isTrue();
        assertThat(provider.resolve(source).discover()).hasSize(3);
        assertThat(provider.resolve(source).getProviderName())
                .isEqualTo(com.viglet.dumont.connector.plugin.catalog.DumCatalogPlugin.PROVIDER_NAME);
    }
}
