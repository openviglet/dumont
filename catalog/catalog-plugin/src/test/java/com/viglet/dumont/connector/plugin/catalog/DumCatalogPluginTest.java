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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.plugin.catalog.fetch.DumCatalogFetcher;
import com.viglet.dumont.connector.plugin.catalog.persistence.model.DumCatalogSource;
import com.viglet.dumont.connector.plugin.catalog.persistence.repository.DumCatalogSourceRepository;
import com.viglet.dumont.connector.plugin.catalog.source.DumCatalogSourceProvider;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/** End-to-end: discover &rarr; extract &rarr; ground &rarr; publish through the structured loop. */
class DumCatalogPluginTest {

    private static final String CATALOG_JSON = """
            [
              { "id": "a", "title": "Curso A", "url": "https://x/a", "description": "desc A",
                "institution": "UFMG" },
              { "id": "b", "title": "Curso B", "url": "https://x/b" },
              { "id": "c", "url": "https://x/c" }
            ]
            """;

    /** Captures every job item the plugin publishes. */
    private static final class CapturingContext implements DumConnectorContext {
        final List<TurSNJobItem> published = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            published.add(item.turSNJobItem());
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            // no-op
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

    private DumCatalogSource ufmg() {
        DumCatalogSource entity = new DumCatalogSource();
        entity.setName("ufmg");
        entity.setCatalogUrl("https://example/ufmg.json");
        entity.setLocale("pt_BR");
        entity.setTurSNSites(Set.of("cursarei"));
        return entity;
    }

    @Test
    void indexAllPublishesValidRecordsAndSkipsThoseMissingMandatoryFields() {
        DumCatalogSource entity = ufmg();
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        when(repository.findByName("ufmg")).thenReturn(Optional.of(entity));

        DumCatalogFetcher fetcher = s -> CATALOG_JSON;
        DumCatalogSourceProvider provider = new DumCatalogSourceProvider(repository, fetcher);
        CapturingContext context = new CapturingContext();
        DumCatalogPlugin plugin = new DumCatalogPlugin(context, provider);

        plugin.indexAll("ufmg");

        // Records "a" and "b" carry title + url; "c" has no title (mandatory) → skipped.
        assertThat(context.published).hasSize(2);
        assertThat(context.published).extracting(TurSNJobItem::getId).containsExactly("a", "b");
        assertThat(context.published.getFirst().getSiteNames()).containsExactly("cursarei");
        // Grounding survives end to end: "b" supplied no description, so the key is absent.
        assertThat(context.published.get(1).getAttributes())
                .doesNotContainKey(DumCatalogSchema.DESCRIPTION);
        assertThat(context.published.getFirst().getAttributes())
                .containsEntry(DumCatalogSchema.DESCRIPTION, "desc A");
    }

    @Test
    void ownsSourceReflectsTheStore() {
        DumCatalogSourceRepository repository = mock(DumCatalogSourceRepository.class);
        when(repository.findByName("ufmg")).thenReturn(Optional.of(ufmg()));
        when(repository.findByName("absent")).thenReturn(Optional.empty());
        DumCatalogPlugin plugin = new DumCatalogPlugin(new CapturingContext(),
                new DumCatalogSourceProvider(repository, s -> CATALOG_JSON));

        assertThat(plugin.ownsSource("ufmg")).isTrue();
        assertThat(plugin.ownsSource("absent")).isFalse();
        assertThat(plugin.getProviderName()).isEqualTo(DumCatalogPlugin.PROVIDER_NAME);
    }
}
