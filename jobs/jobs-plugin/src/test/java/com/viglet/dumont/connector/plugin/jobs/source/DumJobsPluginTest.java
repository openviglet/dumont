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

package com.viglet.dumont.connector.plugin.jobs.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeRef;
import com.viglet.dumont.connector.plugin.jobs.DumJobsPlugin;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.occupation.DumCboFamilies;
import com.viglet.dumont.connector.plugin.jobs.source.occupation.DumJobOccupationEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryEnricher;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/** End-to-end: fetch &rarr; remember &rarr; extract &rarr; ground &rarr; publish through the structured loop. */
class DumJobsPluginTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    /** Captures every job item the plugin publishes. */
    private static final class CapturingContext implements DumConnectorContext {
        final List<TurSNJobItem> published = new ArrayList<>();
        final List<Boolean> finishedStandalone = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            published.add(item.turSNJobItem());
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            finishedStandalone.add(standalone);
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

    @Test
    void indexAllRepublishesSeenPostingsAndSkipsThoseMissingMandatoryFields() {
        DumJobSource entity = DumJobSource.builder().id("s1").name("adzuna-br").sourceType("adzuna")
                .turSNSites(Set.of("trabalharei")).build();
        DumJobSourceRepository repository = mock(DumJobSourceRepository.class);
        when(repository.findByName("adzuna-br")).thenReturn(Optional.of(entity));
        InMemorySeenRepository seen = new InMemorySeenRepository();
        seen.seed("s1", "adzuna-old", NOW.plusSeconds(86_400),
                "{\"id\":\"adzuna-old\",\"title\":\"Analista\",\"source\":\"adzuna\","
                        + "\"url\":\"https://x/old\",\"applyUrl\":\"https://x/old/apply\","
                        + "\"publishedAt\":\"2026-09-20T00:00:00Z\","
                        + "\"modificationDate\":\"2026-09-20T00:00:00Z\"}");

        DumJobSourceAdapter adapter = new DumJobSourceAdapter() {
            @Override
            public String id() {
                return "adzuna";
            }

            @Override
            public List<Map<String, Object>> fetch(DumJobFetchContext context) {
                return List.of(
                        Map.of("id", "adzuna-1", "title", "Desenvolvedor Java", "source", "adzuna",
                                "url", "https://x/1", "applyUrl", "https://x/1/apply",
                                "publishedAt", NOW, "modificationDate", NOW, "city", "São Paulo"),
                        // No applyUrl (mandatory) → skipped, never published half-empty.
                        Map.of("id", "adzuna-2", "title", "Sem link", "source", "adzuna",
                                "url", "https://x/2", "publishedAt", NOW, "modificationDate", NOW));
            }
        };
        DumJobsSourceProvider provider = new DumJobsSourceProvider(repository, seen.repository,
                new InMemoryCallRepository().repository, List.of(adapter), new MutableClock(NOW));
        CapturingContext context = new CapturingContext();

        new DumJobsPlugin(context, provider).indexAll("adzuna-br");

        assertThat(context.published).extracting(TurSNJobItem::getId)
                .containsExactly("adzuna-1", "adzuna-old");
        assertThat(context.published.getFirst().getSiteNames()).containsExactly("trabalharei");
        assertThat(context.published.getFirst().getAttributes())
                .containsEntry("city", List.of("São Paulo"))
                .doesNotContainKey("salaryMin");
        assertThat(context.finishedStandalone).containsExactly(false);
    }

    @Test
    void indexAllPublishesTheOccupationFromTheDictionaryOrTheCappedCachedClassifier() {
        DumJobSource entity = DumJobSource.builder().id("s2").name("jooble-br").sourceType("jooble")
                .turSNSites(Set.of("trabalharei")).build();
        DumJobSourceRepository repository = mock(DumJobSourceRepository.class);
        when(repository.findByName("jooble-br")).thenReturn(Optional.of(entity));
        DumJobSourceAdapter adapter = new DumJobSourceAdapter() {
            @Override
            public String id() {
                return "jooble";
            }

            @Override
            public List<Map<String, Object>> fetch(DumJobFetchContext context) {
                return List.of(posting("j-1", "Motorista Carreteiro"), posting("j-2", "Projetista de Moldes"),
                        posting("j-3", "Projetista de Moldes Sênior"), posting("j-4", "Jovem Aprendiz"),
                        posting("j-5", "Modelista de Calçados"));
            }
        };
        MutableClock clock = new MutableClock(NOW);
        InMemoryCallRepository calls = new InMemoryCallRepository();
        DumJobsSourceProvider provider = new DumJobsSourceProvider(repository,
                new InMemorySeenRepository().repository, calls.repository, List.of(adapter), clock);
        List<String> asked = new ArrayList<>();
        // A daily cap of one: the first unknown title is classified, the second is refused.
        DumJobOccupationEnricher occupation = new DumJobOccupationEnricher(DumJobOccupationEnricher.library(),
                DumRecipeRef.latest(DumCboFamilies.TAXONOMY), title -> {
                    asked.add(title);
                    return Optional.of("3188");
                }, null, calls.repository, 1, clock);
        CapturingContext context = new CapturingContext();

        new DumJobsPlugin(context, provider, DumJobLocationEnricher.standard(), new DumJobSalaryEnricher(),
                occupation).indexAll("jooble-br");

        Map<String, Object> byId = new java.util.HashMap<>();
        context.published.forEach(item -> byId.put(item.getId(), item.getAttributes().get("occupation")));
        assertThat(byId).containsEntry("j-1", List.of("motoristas-de-veiculos-de-cargas-em-geral"))
                .containsEntry("j-2",
                        List.of("desenhistas-projetistas-e-modelistas-de-produtos-e-servicos-diversos"))
                .containsEntry("j-3",
                        List.of("desenhistas-projetistas-e-modelistas-de-produtos-e-servicos-diversos"));
        assertThat(byId.get("j-4")).isNull();
        assertThat(byId.get("j-5")).isNull();
        assertThat(asked).containsExactly("Projetista de Moldes");
        assertThat(calls.calls(DumJobOccupationEnricher.LLM_CALLS_ID)).containsExactly(NOW);
        // The run's counts were logged and cleared.
        assertThat(occupation.summary("jooble-br"))
                .isEqualTo(new DumJobOccupationEnricher.Summary(0, 0, 0, 0, 0, 0));
    }

    private static Map<String, Object> posting(String id, String title) {
        return Map.of("id", id, "title", title, "source", "jooble", "url", "https://x/" + id,
                "applyUrl", "https://x/" + id + "/apply", "publishedAt", NOW, "modificationDate", NOW);
    }
}
