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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.enrich.DumEnrichmentChain;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeLibrary;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumRecipeRef;
import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;
import com.viglet.dumont.connector.plugin.jobs.DumJobsPlugin;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumIbgeMunicipalities;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher.Summary;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/** The location enricher as the jobs connector runs it: on every record, between extract and publish. */
class DumJobLocationEnricherTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private final InMemorySeenRepository seen = new InMemorySeenRepository();

    private static Map<String, Object> job(String id, Object... place) {
        Map<String, Object> job = new HashMap<>();
        job.put("id", id);
        job.put("title", "Vaga " + id);
        job.put("source", "jooble");
        job.put("url", "https://x/" + id);
        job.put("applyUrl", "https://x/" + id + "/apply");
        job.put("publishedAt", NOW.toString());
        job.put("modificationDate", NOW.toString());
        for (int i = 0; i < place.length; i += 2) {
            job.put((String) place[i], place[i + 1]);
        }
        return job;
    }

    private static final List<Map<String, Object>> FETCHED = List.of(
            // Jooble keeps the raw text; the mapper's split city/state ride along.
            job("a", "city", "São Paulo", "state", "SP", "locationRaw", "São Paulo - SP"),
            // BNE-style: upper case, no accents, invalid state.
            job("b", "city", "SAO JOSE DOS CAMPOS", "state", "XX"),
            // A city that is not in its UF: the UF stays, the city goes.
            job("c", "city", "Recife", "state", "SP"),
            // Only the raw text, and it says remote.
            job("d", "locationRaw", "100% remoto"),
            // Unresolvable.
            job("e", "locationRaw", "Xyzlândia"),
            // No place at all.
            job("f"));

    private DumJobsStructuredSource source(List<Map<String, Object>> fetched) {
        DumJobSource entity = DumJobSource.builder().id("s1").name("jooble-br").sourceType("jooble")
                .turSNSites(Set.of("trabalharei")).build();
        DumJobSourceAdapter adapter = new DumJobSourceAdapter() {
            @Override
            public String id() {
                return "jooble";
            }

            @Override
            public List<Map<String, Object>> fetch(DumJobFetchContext context) {
                return fetched;
            }
        };
        return new DumJobsStructuredSource(entity, DumJobsSchema.schema(), adapter, seen.repository,
                new InMemoryCallRepository().repository, mock(DumJobSourceRepository.class),
                new MutableClock(NOW));
    }

    private static Map<String, DumStructuredRecord> enrichAll(DumJobsStructuredSource source,
            DumJobLocationEnricher enricher) {
        DumEnrichmentChain chain = DumEnrichmentChain.of(enricher);
        Map<String, DumStructuredRecord> out = new HashMap<>();
        for (String id : source.discover()) {
            out.put(id, chain.apply(source, source.extract(id).orElseThrow()));
        }
        return out;
    }

    @Test
    void extractLeavesCityAndStateToTheEnricher() {
        DumJobsStructuredSource source = source(FETCHED);
        source.discover();
        DumStructuredRecord raw = source.extract("a").orElseThrow();
        assertThat(raw.has("city")).isFalse();
        assertThat(raw.has("state")).isFalse();
        // D117: the adapter's place travels as transients, never as values.
        assertThat(raw.transients()).containsEntry("city", "São Paulo")
                .containsEntry("locationRaw", "São Paulo - SP");
    }

    @Test
    void writesTheCanonicalCityAndUfOnEveryRecord() {
        DumJobsStructuredSource source = source(FETCHED);
        DumJobLocationEnricher enricher = DumJobLocationEnricher.standard();
        Map<String, DumStructuredRecord> records = enrichAll(source, enricher);

        assertThat(records.get("a").values()).containsEntry("city", List.of("São Paulo"))
                .containsEntry("state", List.of("SP")).doesNotContainKey("remote");
        assertThat(records.get("a").provenance().get("city").toString())
                .contains(DumJobLocationEnricher.NAME);
        assertThat(records.get("b").values()).containsEntry("city", List.of("São José dos Campos"))
                .containsEntry("state", List.of("SP"));
        assertThat(records.get("c").values()).containsEntry("state", List.of("SP"))
                .doesNotContainKey("city");
        assertThat(records.get("d").values()).containsEntry("remote", List.of("remoto"))
                .doesNotContainKeys("city", "state");
        assertThat(records.get("e").values()).doesNotContainKeys("city", "state", "remote");
        assertThat(records.get("f").values()).doesNotContainKeys("city", "state", "remote");
        // Record keys that are not schema fields never reach the record.
        records.values().forEach(rec -> assertThat(rec.values())
                .doesNotContainKeys("locationRaw", "locationUnresolved"));
    }

    @Test
    void unresolvedPlacesAreKeptRawOnTheRecordAndCountedPerSource() {
        DumJobsStructuredSource source = source(FETCHED);
        DumJobLocationEnricher enricher = DumJobLocationEnricher.standard();
        Map<String, DumStructuredRecord> records = enrichAll(source, enricher);

        assertThat(records.get("c").transients()).containsEntry("locationUnresolved", "Recife, SP");
        assertThat(records.get("e").transients()).containsEntry("locationUnresolved", "Xyzlândia");
        assertThat(records.get("a").transients()).doesNotContainKey("locationUnresolved");
        assertThat(records.get("f").transients()).doesNotContainKey("locationUnresolved");

        assertThat(enricher.summary("jooble-br")).isEqualTo(new Summary(6, 3, 2));
        assertThat(enricher.summary("other")).isEqualTo(new Summary(0, 0, 0));
        assertThat(enricher.logSummary("jooble-br")).isEqualTo(new Summary(6, 3, 2));
        // Logged once, then cleared for the next run.
        assertThat(enricher.summary("jooble-br")).isEqualTo(new Summary(0, 0, 0));
    }

    @Test
    void aTaxonomyBumpReAppliesWithoutRebuildingTheChain() {
        DumRecipeLibrary library = new DumRecipeLibrary()
                .register(DumTaxonomy.builder(DumIbgeMunicipalities.TAXONOMY, 1)
                        .map("3509502", "Campinas").build());
        DumJobLocationEnricher enricher = new DumJobLocationEnricher(library,
                DumRecipeRef.latest(DumIbgeMunicipalities.TAXONOMY));
        DumJobsStructuredSource source = source(List.of(job("g", "city", "Natal", "state", "RN")));

        assertThat(enrichAll(source, enricher).get("g").values()).doesNotContainKey("city")
                .containsEntry("state", List.of("RN"));

        library.register(DumIbgeMunicipalities.load());
        assertThat(enrichAll(source(List.of(job("g", "city", "Natal", "state", "RN"))), enricher)
                .get("g").values()).containsEntry("city", List.of("Natal"));
    }

    @Test
    void withoutTheTaxonomyTheAdaptersValuesPassThrough() {
        DumJobLocationEnricher enricher = new DumJobLocationEnricher(new DumRecipeLibrary(),
                DumRecipeRef.latest(DumIbgeMunicipalities.TAXONOMY));
        DumStructuredRecord rec = enrichAll(source(List.of(job("h", "city", "Belo  Horizonte",
                "state", "mg"))), enricher).get("h");
        assertThat(rec.values()).containsEntry("city", List.of("Belo  Horizonte"))
                .containsEntry("state", List.of("MG"));
    }

    // --- through the plugin -------------------------------------------------------------------

    private static final class CapturingContext implements DumConnectorContext {
        final List<TurSNJobItem> published = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            published.add(item.turSNJobItem());
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            // nothing to finish
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
    void thePluginPublishesCanonicalPlacesAndClosesTheCountAtRunEnd() {
        DumJobSource entity = DumJobSource.builder().id("s1").name("jooble-br").sourceType("jooble")
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
                return FETCHED;
            }
        };
        DumJobsSourceProvider provider = new DumJobsSourceProvider(repository, seen.repository,
                new InMemoryCallRepository().repository, List.of(adapter), new MutableClock(NOW));
        CapturingContext context = new CapturingContext();
        DumJobsPlugin plugin = new DumJobsPlugin(context, provider);

        plugin.indexAll("jooble-br");

        Map<String, Map<String, Object>> byId = new HashMap<>();
        context.published.forEach(item -> byId.put(item.getId(), item.getAttributes()));
        assertThat(byId).hasSize(6);
        assertThat(byId.get("b")).containsEntry("city", List.of("São José dos Campos"))
                .containsEntry("state", List.of("SP"));
        assertThat(byId.get("c")).doesNotContainKey("city").containsEntry("state", List.of("SP"));
        assertThat(byId.get("d")).containsEntry("remote", List.of("remoto"));
        byId.values().forEach(attributes -> assertThat(attributes)
                .doesNotContainKeys("locationRaw", "locationUnresolved"));
        assertThat(plugin.locationEnricher().summary("jooble-br")).isEqualTo(new Summary(0, 0, 0));
    }
}
