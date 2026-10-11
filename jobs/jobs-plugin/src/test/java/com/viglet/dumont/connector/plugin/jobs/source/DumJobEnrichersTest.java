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
import com.viglet.dumont.connector.plugin.jobs.DumJobsPlugin;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.contract.DumJobContractEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryEnricher.Summary;
import com.viglet.dumont.connector.plugin.jobs.source.seniority.DumJobSeniorityEnricher;
import com.viglet.turing.client.sn.job.TurSNJobItem;

/** The salary, contract and seniority steps as the jobs connector runs them (trabalharei TRA21). */
class DumJobEnrichersTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private static Map<String, Object> job(String id, Object... values) {
        Map<String, Object> job = new HashMap<>();
        job.put("id", id);
        job.put("title", "Vaga " + id);
        job.put("source", "jooble");
        job.put("url", "https://x/" + id);
        job.put("applyUrl", "https://x/" + id + "/apply");
        job.put("publishedAt", NOW.toString());
        job.put("modificationDate", NOW.toString());
        for (int i = 0; i < values.length; i += 2) {
            job.put((String) values[i], values[i + 1]);
        }
        return job;
    }

    private static DumJobSourceAdapter adapter(List<Map<String, Object>> fetched) {
        return new DumJobSourceAdapter() {
            @Override
            public String id() {
                return "jooble";
            }

            @Override
            public List<Map<String, Object>> fetch(DumJobFetchContext context) {
                return fetched;
            }
        };
    }

    private static DumJobSource entity() {
        return DumJobSource.builder().id("s1").name("jooble-br").sourceType("jooble")
                .turSNSites(Set.of("trabalharei")).build();
    }

    @SafeVarargs
    private static EnrichedRun run(Map<String, Object>... fetched) {
        return EnrichedRun.of(adapter(List.of(fetched)), entity(), new MutableClock(NOW));
    }

    // --- salary --------------------------------------------------------------------------------

    @Test
    void unparsedSalaryTextIsKeptRawOnTheRecordAndCounted() {
        EnrichedRun run = run(job("a", "salaryRaw", "R$ 25"), job("b", "salaryRaw", "US$ 3,000"),
                job("c", "salaryRaw", "A combinar"), job("d", "salaryRaw", "R$ 15/hora"), job("e"));

        // D117: the note is a transient on the enriched record, not a key written back to the source.
        assertThat(run.records.get("a").transients()).containsEntry("salaryUnparsed", "R$ 25");
        assertThat(run.records.get("b").transients()).containsEntry("salaryUnparsed", "US$ 3,000");
        assertThat(run.records.get("c").transients()).doesNotContainKey("salaryUnparsed");
        assertThat(run.records.get("e").transients()).doesNotContainKey("salaryUnparsed");
        for (String id : List.of("a", "b", "c", "e")) {
            assertThat(run.values(id)).doesNotContainKeys("salaryMin", "salaryMax", "salaryPeriod",
                    "salaryBand", "salaryRaw", "salaryUnparsed");
        }
        assertThat(run.values("d")).containsEntry("salaryMin", 3300.0).containsEntry("salaryPeriod", "hora");
        assertThat(run.salary.summary("jooble-br")).isEqualTo(new Summary(5, 0, 1, 2));
        assertThat(run.salary.logSummary("jooble-br")).isEqualTo(new Summary(5, 0, 1, 2));
        assertThat(run.salary.summary("jooble-br")).isEqualTo(new Summary(0, 0, 0, 0));
    }

    @Test
    void partnerNumbersWinOverTextAndAreNeverReparsed() {
        EnrichedRun run = run(
                // Numbers and text: the numbers are the partner's, the text is not read.
                job("a", "salaryMin", 2000, "salaryPeriod", "mes", "salaryRaw", "R$ 9.000"),
                // A band the posting already carries is kept.
                job("b", "salaryMin", "5000.5", "salaryBand", "5k-8k"),
                // A malformed number is dropped; the valid one stays.
                job("c", "salaryMin", "a combinar", "salaryMax", 5000.5),
                // A period outside the vocabulary is dropped, the amount stays.
                job("d", "salaryMin", 4000, "salaryPeriod", "semana"));

        assertThat(run.values("a")).containsEntry("salaryMin", 2000.0).containsEntry("salaryPeriod", "mes")
                .containsEntry("salaryBand", List.of("1.5k-3k")).doesNotContainKey("salaryMax");
        assertThat(run.values("b")).containsEntry("salaryMin", 5000.5)
                .containsEntry("salaryBand", List.of("5k-8k"));
        // No minimum, so no band.
        assertThat(run.values("c")).containsEntry("salaryMax", 5000.5)
                .doesNotContainKeys("salaryMin", "salaryBand");
        assertThat(run.values("d")).containsEntry("salaryMin", 4000.0).doesNotContainKey("salaryPeriod");
        assertThat(run.salary.summary("jooble-br")).isEqualTo(new Summary(4, 4, 0, 0));
    }

    // --- contract ------------------------------------------------------------------------------

    @Test
    void contractFromThePartnerValueThenTitleThenText() {
        EnrichedRun run = run(
                // The partner's value wins over title and text.
                job("a", "contract", "pj", "title", "Estagiário de Direito", "text", "Regime CLT"),
                // A partner value outside the vocabulary is read for keywords.
                job("b", "contract", "CLT (Efetivo)"),
                // A partner value naming no kind falls through to the title.
                job("c", "contract", "Full-time", "title", "Estágio em RH"),
                // The title, then the text.
                job("d", "title", "Analista Fiscal", "text", "Contratação CLT, vale-refeição."),
                // An ambiguous title ends the search: the text is not used to settle it.
                job("e", "title", "Analista CLT ou PJ", "text", "Contratação CLT."),
                // An ambiguous partner value ends it too.
                job("f", "contract", "CLT ou PJ", "title", "Estágio em RH"),
                // Plurals name the people managed.
                job("g", "title", "Supervisor de Estagiários"),
                job("h", "title", "Programa de Trainee 2027"));

        assertThat(run.values("a")).containsEntry("contract", List.of("pj"));
        assertThat(run.values("b")).containsEntry("contract", List.of("clt"));
        assertThat(run.values("c")).containsEntry("contract", List.of("estagio"));
        assertThat(run.values("d")).containsEntry("contract", List.of("clt"));
        assertThat(run.values("e")).doesNotContainKey("contract");
        assertThat(run.values("f")).doesNotContainKey("contract");
        assertThat(run.values("g")).doesNotContainKey("contract");
        assertThat(run.values("h")).containsEntry("contract", List.of("trainee"));
        assertThat(run.records.get("a").provenance().get("contract").toString())
                .contains(DumJobContractEnricher.NAME);
    }

    // --- seniority -----------------------------------------------------------------------------

    @Test
    void seniorityFromTheTitleOnly() {
        EnrichedRun run = run(job("a", "title", "Analista Sr"),
                // A partner value is not a title token, and the text is never read.
                job("b", "title", "Analista de Dados", "seniority", "senior", "text", "Vaga para júnior"),
                job("c", "title", "Cuidador para Sr. Idoso"),
                job("d", "title", "Analista Jr/Pl"));

        assertThat(run.values("a")).containsEntry("seniority", List.of("senior"));
        assertThat(run.values("b")).doesNotContainKey("seniority");
        assertThat(run.values("c")).doesNotContainKey("seniority");
        assertThat(run.values("d")).doesNotContainKey("seniority");
        assertThat(run.records.get("a").provenance().get("seniority").toString())
                .contains(DumJobSeniorityEnricher.NAME);
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
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source, String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider,
                List<String> dependenciesObjectIdList) {
            return List.of();
        }
    }

    @Test
    void thePluginPublishesTheCanonicalFieldsAndClosesTheSalaryCount() {
        DumJobSourceRepository repository = mock(DumJobSourceRepository.class);
        when(repository.findByName("jooble-br")).thenReturn(Optional.of(entity()));
        DumJobsSourceProvider provider = new DumJobsSourceProvider(repository,
                new InMemorySeenRepository().repository, new InMemoryCallRepository().repository,
                List.of(adapter(List.of(
                        job("a", "title", "Desenvolvedor Java Pleno", "contract", "clt",
                                "salaryRaw", "R$ 5.000 a R$ 7.000 por mês"),
                        job("b", "title", "Estagiária de Marketing", "salaryRaw", "R$ 10")))),
                new MutableClock(NOW));
        CapturingContext context = new CapturingContext();
        DumJobsPlugin plugin = new DumJobsPlugin(context, provider);

        plugin.indexAll("jooble-br");

        Map<String, Map<String, Object>> byId = new HashMap<>();
        context.published.forEach(item -> byId.put(item.getId(), item.getAttributes()));
        assertThat(byId.get("a")).containsEntry("contract", List.of("clt"))
                .containsEntry("seniority", List.of("pleno"))
                .containsEntry("salaryBand", List.of("3k-5k"))
                .containsEntry("salaryPeriod", "mes");
        assertThat(byId.get("b")).containsEntry("contract", List.of("estagio"))
                .containsEntry("seniority", List.of("estagio"))
                .doesNotContainKeys("salaryMin", "salaryBand");
        byId.values().forEach(attributes -> assertThat(attributes)
                .doesNotContainKeys("salaryRaw", "salaryUnparsed"));
        assertThat(plugin.salaryEnricher()).isInstanceOf(DumJobSalaryEnricher.class);
        assertThat(plugin.salaryEnricher().summary("jooble-br")).isEqualTo(new Summary(0, 0, 0, 0));
    }
}
