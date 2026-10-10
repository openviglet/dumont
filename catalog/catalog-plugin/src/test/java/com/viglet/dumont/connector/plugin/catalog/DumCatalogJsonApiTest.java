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

package com.viglet.dumont.connector.plugin.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiRequest;
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumJsonApiSource;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.dumont.connector.plugin.catalog.export.DumCatalogExchangeProcess;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;
import com.viglet.dumont.connector.plugin.catalog.source.DumCatalogSourceProvider;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/** A catalog source bound to a JSON API manifest and its secrets (roadmap D92). */
class DumCatalogJsonApiTest {

    private static final String MANIFEST = """
            {"name": "jobs", "request": {"url": "https://api.example/jobs?p={page}&key={secret:key}"},
             "paging": {"mode": "page", "size": 2}, "records": "results", "id": "id",
             "fields": [{"name": "title", "type": "TEXT", "mandatory": true},
                        {"name": "company", "path": "company.name", "facet": true}]}
            """;

    private final List<DumJsonApiRequest> requests = new ArrayList<>();

    @Test
    void aManifestSourceIndexesThroughThePlugin() {
        DumCatalogSourceRepository repository = repositoryWith(jobs());
        CapturingContext context = new CapturingContext();

        new DumCatalogPlugin(context, provider(repository)).indexAll("jobs");

        assertThat(context.published).extracting(TurSNJobItem::getId).containsExactly("1", "2", "3");
        assertThat(context.published.getFirst().getAttributes()).containsEntry("company", "Acme");
        assertThat(context.published.getFirst().getSiteNames()).containsExactly("vagas");
        assertThat(requests).extracting(DumJsonApiRequest::url).containsExactly(
                "https://api.example/jobs?p=1&key=s3cret", "https://api.example/jobs?p=2&key=s3cret");
        assertThat(context.standalone).isFalse();
    }

    @Test
    void theManifestTakesPrecedenceOverTheCatalogUrl() {
        DumCatalogSource source = jobs();
        source.setCatalogUrl("https://engine.example/legacy.json");

        assertThat(provider(repositoryWith(source)).resolve(source)).isInstanceOf(DumJsonApiSource.class);
    }

    @Test
    void aSourceWhoseManifestCannotRunIsNotOffered() {
        DumCatalogSource source = jobs();
        source.setJsonApiSecrets("{}");

        assertThat(provider(repositoryWith(source)).resolve("jobs")).isEmpty();
    }

    @Test
    void validationNamesWhatIsWrong() {
        DumCatalogSource missingSecret = jobs();
        missingSecret.setJsonApiSecrets(null);
        assertThatThrownBy(() -> DumCatalogSourceProvider.validateJsonApi(missingSecret))
                .hasMessageContaining("[key]");

        DumCatalogSource badSecrets = jobs();
        badSecrets.setJsonApiSecrets("key=s3cret");
        assertThatThrownBy(() -> DumCatalogSourceProvider.validateJsonApi(badSecrets))
                .hasMessageContaining("JSON object");

        DumCatalogSource badManifest = jobs();
        badManifest.setJsonApiManifest("{\"name\": \"jobs\"}");
        assertThatThrownBy(() -> DumCatalogSourceProvider.validateJsonApi(badManifest))
                .isInstanceOf(IllegalArgumentException.class);

        DumCatalogSource sidecar = jobs();
        sidecar.setJsonApiManifest(" ");
        DumCatalogSourceProvider.validateJsonApi(sidecar);
    }

    @Test
    void anExportCarriesTheManifestButNeverItsSecrets() {
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        when(repository.findAll()).thenReturn(List.of(jobs()));

        JSONObject exported = new JSONArray(new DumCatalogExchangeProcess(repository).exportJson())
                .getJSONObject(0);

        assertThat(exported.getJSONObject("jsonApiManifest").getString("name")).isEqualTo("jobs");
        assertThat(exported.has("jsonApiSecrets")).isFalse();
        assertThat(exported.toString()).doesNotContain("s3cret");
    }

    @Test
    void anImportReadsAManifestSourceWithoutACatalogUrl() {
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        when(repository.findByName("jobs")).thenReturn(Optional.empty());

        new DumCatalogExchangeProcess(repository).importFromString("""
                [{"name": "jobs", "turSNSites": ["vagas"], "jsonApiManifest": %s,
                  "jsonApiSecrets": {"key": "s3cret"}}]
                """.formatted(MANIFEST));

        ArgumentCaptor<DumCatalogSource> saved = ArgumentCaptor.forClass(DumCatalogSource.class);
        verify(repository).save(saved.capture());
        assertThat(new JSONObject(saved.getValue().getJsonApiManifest()).getString("name"))
                .isEqualTo("jobs");
        assertThat(DumCatalogSourceProvider.secretsOf(saved.getValue())).isEqualTo(Map.of("key", "s3cret"));
    }

    // --- helpers -------------------------------------------------------------------------------

    private DumCatalogSourceProvider provider(DumCatalogSourceRepository repository) {
        return new DumCatalogSourceProvider(repository, source -> "[]", new DumRecipeLibrary(),
                DumHtmlFetcher.ofPages(Map.of()), request -> {
                    requests.add(request);
                    return requests.size() == 1
                            ? page(new int[] {1, 2})
                            : page(new int[] {3});
                });
    }

    private static DumCatalogSourceRepository repositoryWith(DumCatalogSource source) {
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        when(repository.findByName(any())).thenReturn(Optional.empty());
        when(repository.findByName(source.getName())).thenReturn(Optional.of(source));
        return repository;
    }

    private static DumCatalogSource jobs() {
        DumCatalogSource source = new DumCatalogSource();
        source.setName("jobs");
        source.setLocale("pt_BR");
        source.setTurSNSites(Set.of("vagas"));
        source.setJsonApiManifest(MANIFEST);
        source.setJsonApiSecrets("{\"key\": \"s3cret\"}");
        return source;
    }

    private static String page(int[] ids) {
        JSONArray results = new JSONArray();
        for (int id : ids) {
            results.put(new JSONObject().put("id", id).put("title", "Job " + id)
                    .put("company", new JSONObject().put("name", "Acme")));
        }
        return new JSONObject().put("results", results).toString();
    }

    private static final class CapturingContext implements DumConnectorContext {
        final List<TurSNJobItem> published = new ArrayList<>();
        boolean standalone;

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            published.add(item.turSNJobItem());
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            this.standalone = standalone;
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
