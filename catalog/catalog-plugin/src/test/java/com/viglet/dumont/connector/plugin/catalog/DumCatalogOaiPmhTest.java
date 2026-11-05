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
import com.viglet.dumont.connector.commons.spi.structured.jsonapi.DumInMemoryJsonApiState;
import com.viglet.dumont.connector.commons.spi.structured.oaipmh.DumOaiPmhSource;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary;
import com.viglet.dumont.connector.commons.spi.structured.strategy.DumHtmlFetcher;
import com.viglet.dumont.connector.plugin.catalog.export.DumCatalogExchangeProcess;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;
import com.viglet.dumont.connector.plugin.catalog.source.DumCatalogSourceProvider;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/** A catalog source bound to an OAI-PMH repository (roadmap D315). */
class DumCatalogOaiPmhTest {

    private static final String BASE = "https://repo.example/oai/request";
    private static final String PAGE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
              <responseDate>2026-11-01T10:00:00Z</responseDate>
              <ListRecords>
                <record><header><identifier>oai:repo:1</identifier><datestamp>2026-10-30</datestamp></header>
                  <metadata><oai_dc:dc xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/"
                      xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Soil carbon</dc:title>
                    <dc:description>Abstract.</dc:description>
                    <dc:identifier>http://hdl.handle.net/123/1</dc:identifier>
                    <dc:identifier>https://repo.example/bitstream/123/1/thesis.pdf</dc:identifier>
                  </oai_dc:dc></metadata></record>
              </ListRecords>
            </OAI-PMH>
            """;

    private final List<String> urls = new ArrayList<>();
    private final List<String> fullTextUrls = new ArrayList<>();

    @Test
    void aRepositorySourceHarvestsThroughThePluginAndKeepsItsWatermarkOnTheRow() {
        DumCatalogSource source = repo();
        DumCatalogSourceRepository repository = repositoryWith(source);

        CapturingContext first = new CapturingContext();
        new DumCatalogPlugin(first, provider(repository)).indexAll("repo");

        assertThat(first.published).extracting(TurSNJobItem::getId).containsExactly("oai:repo:1");
        assertThat(first.published.getFirst().getAttributes()).containsEntry("url", "http://hdl.handle.net/123/1");
        assertThat(first.published.getFirst().getSiteNames()).containsExactly("acervo");
        assertThat(urls).containsExactly(BASE + "?verb=ListRecords&metadataPrefix=oai_dc&set=theses");
        assertThat(first.standalone).as("a first, full harvest sweeps").isFalse();
        assertThat(source.getOaiPmhWatermark()).isEqualTo("2026-11-01T10:00:00Z");

        urls.clear();
        CapturingContext second = new CapturingContext();
        new DumCatalogPlugin(second, provider(repository)).indexAll("repo");
        assertThat(urls.getFirst()).endsWith("&from=2026-11-01");
        assertThat(second.standalone).as("an incremental harvest never sweeps").isTrue();
    }

    @Test
    void fullTextIsReadOnlyWhenTheSourceAsksForIt() {
        DumCatalogSource source = repo();
        DumCatalogSourceRepository repository = repositoryWith(source);

        CapturingContext off = new CapturingContext();
        new DumCatalogPlugin(off, provider(repository)).indexAll("repo");
        assertThat(off.published.getFirst().getAttributes()).containsEntry("text", "Abstract.");
        assertThat(fullTextUrls).isEmpty();

        source.setOaiPmhFullText(true);
        source.setOaiPmhWatermark(null);
        CapturingContext on = new CapturingContext();
        new DumCatalogPlugin(on, provider(repository)).indexAll("repo");
        assertThat(on.published.getFirst().getAttributes().get("text").toString())
                .startsWith("Abstract.").endsWith("The thesis itself.");
        assertThat(fullTextUrls).containsExactly(
                "https://repo.example/bitstream/123/1/thesis.pdf @ repo.example");
    }

    @Test
    void theRepositoryTakesPrecedenceOverTheCatalogUrl() {
        DumCatalogSource source = repo();
        source.setCatalogUrl("https://engine.example/legacy.json");

        assertThat(provider(repositoryWith(source)).resolve(source)).isInstanceOf(DumOaiPmhSource.class);
    }

    @Test
    void validationRefusesANonHttpUrlAndASecondBinding() {
        DumCatalogSource ftp = repo();
        ftp.setOaiPmhBaseUrl("ftp://repo.example/oai");
        assertThatThrownBy(() -> DumCatalogSourceProvider.validateOaiPmh(ftp))
                .hasMessageContaining("http(s)");

        DumCatalogSource both = repo();
        both.setJsonApiManifest("{}");
        assertThatThrownBy(() -> DumCatalogSourceProvider.validateOaiPmh(both))
                .hasMessageContaining("not both");

        DumCatalogSourceProvider.validateOaiPmh(repo());
    }

    @Test
    void anExportCarriesTheBindingButNotTheWatermark() {
        DumCatalogSource source = repo();
        source.setOaiPmhWatermark("2026-11-01T10:00:00Z");
        source.setOaiPmhFullText(true);
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        when(repository.findAll()).thenReturn(List.of(source));

        JSONObject exported = new JSONArray(new DumCatalogExchangeProcess(repository).exportJson())
                .getJSONObject(0);

        assertThat(exported.getString("oaiPmhBaseUrl")).isEqualTo(BASE);
        assertThat(exported.getString("oaiPmhSet")).isEqualTo("theses");
        assertThat(exported.getBoolean("oaiPmhFullText")).isTrue();
        assertThat(exported.toString()).doesNotContain("2026-11-01");
    }

    @Test
    void anImportReadsARepositorySourceWithoutACatalogUrl() {
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        when(repository.findByName("repo")).thenReturn(Optional.empty());

        new DumCatalogExchangeProcess(repository).importFromString("""
                [{"name": "repo", "turSNSites": ["acervo"], "oaiPmhBaseUrl": "%s", "oaiPmhSet": "theses",
                  "oaiPmhFullText": true}]
                """.formatted(BASE));

        ArgumentCaptor<DumCatalogSource> saved = ArgumentCaptor.forClass(DumCatalogSource.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOaiPmhBaseUrl()).isEqualTo(BASE);
        assertThat(saved.getValue().getOaiPmhSet()).isEqualTo("theses");
        assertThat(saved.getValue().getOaiPmhMetadataPrefix()).isNull();
        assertThat(saved.getValue().getOaiPmhFullText()).isTrue();
    }

    // --- helpers -------------------------------------------------------------------------------

    private DumCatalogSourceProvider provider(DumCatalogSourceRepository repository) {
        return new DumCatalogSourceProvider(repository, source -> "[]", new DumRecipeLibrary(),
                DumHtmlFetcher.ofPages(Map.of()), request -> "[]", name -> new DumInMemoryJsonApiState(),
                url -> {
                    urls.add(url);
                    return PAGE;
                }, (url, host) -> {
                    fullTextUrls.add(url + " @ " + host);
                    return Optional.of("The thesis itself.");
                });
    }

    private static DumCatalogSourceRepository repositoryWith(DumCatalogSource source) {
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        when(repository.findByName(any())).thenReturn(Optional.empty());
        when(repository.findByName(source.getName())).thenReturn(Optional.of(source));
        return repository;
    }

    private static DumCatalogSource repo() {
        DumCatalogSource source = new DumCatalogSource();
        source.setName("repo");
        source.setLocale("pt_BR");
        source.setTurSNSites(Set.of("acervo"));
        source.setOaiPmhBaseUrl(BASE);
        source.setOaiPmhSet("theses");
        return source;
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
