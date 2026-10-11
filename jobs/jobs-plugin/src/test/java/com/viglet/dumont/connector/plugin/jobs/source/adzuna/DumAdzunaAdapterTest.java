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

package com.viglet.dumont.connector.plugin.jobs.source.adzuna;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget.Window;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobFetchContext;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobsSourceProvider;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobsStructuredSource;
import com.viglet.dumont.connector.plugin.jobs.source.InMemoryCallRepository;
import com.viglet.dumont.connector.plugin.jobs.source.InMemorySeenRepository;
import com.viglet.dumont.connector.plugin.jobs.source.MutableClock;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;

/** The Adzuna adapter against recorded fixtures: mapping, paging, budget, rotation. */
class DumAdzunaAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String SOURCE_ID = "src-adzuna";
    /** Rotation off, so a test sees only the newest-first pass. */
    private static final String NO_ROTATION = "{\"categories\":[]}";

    private final MutableClock clock = new MutableClock(NOW);
    private final FixtureFetcher fetcher = new FixtureFetcher();
    private final DumAdzunaAdapter adapter = new DumAdzunaAdapter(fetcher, clock);

    private static DumJobSource source(String settings) {
        return DumJobSource.builder().id(SOURCE_ID).name("adzuna-br").sourceType("adzuna")
                .appId("my-app-id").appKey("my-app-key").settings(settings)
                .turSNSites(Set.of("trabalharei")).build();
    }

    private List<Map<String, Object>> fetch(DumJobSource source, Set<String> seen) {
        return adapter.fetch(new DumJobFetchContext(source, DumJobCallBudget.unlimited(clock),
                seen::contains));
    }

    private Map<String, Map<String, Object>> byId(List<Map<String, Object>> jobs) {
        return jobs.stream().collect(Collectors.toMap(j -> j.get("id").toString(),
                Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));
    }

    // --- mapping ------------------------------------------------------------------------------

    @Test
    void mapsEveryFieldOfAPostingWithAStatedSalary() {
        Map<String, Object> job = byId(fetch(source(NO_ROTATION), Set.of())).get("adzuna-4876543210");

        assertThat(job).containsOnlyKeys("id", "title", "text", "company", "city", "state", "remote",
                "contract", "salaryMin", "salaryMax", "salaryPeriod", "source", "url",
                "applyUrl", "publishedAt", "modificationDate");
        assertThat(job)
                .containsEntry("id", "adzuna-4876543210")
                .containsEntry("title", "Desenvolvedor Java Pleno")
                .containsEntry("company", "Tech Brasil Ltda")
                .containsEntry("city", "Campinas")
                .containsEntry("state", "SP")
                .containsEntry("remote", "remoto")
                .containsEntry("contract", "clt")
                .containsEntry("salaryMin", 5000.0)
                .containsEntry("salaryMax", 7000.0)
                // TRA21: monthly amounts, the original (yearly) period; the band is the salary
                // enricher's.
                .containsEntry("salaryPeriod", "ano")
                .containsEntry("source", "adzuna")
                .containsEntry("url",
                        "https://trabalharei.com.br/vaga/desenvolvedor-java-pleno-adzuna-4876543210/")
                .containsEntry("publishedAt", "2026-10-10T09:15:00Z")
                .containsEntry("modificationDate", "2026-10-10T09:15:00Z");
        assertThat(job.get("text").toString()).startsWith("Buscamos Desenvolvedor Java Pleno")
                .contains("100% remota");
        // Left for later stages, never invented here.
        assertThat(job).doesNotContainKeys("abstract", "seniority", "occupation", "expiresAt",
                "salaryBand");
        // Every key is a schema field.
        assertThat(DumJobsSchema.FIELDS).containsAll(job.keySet());
    }

    @Test
    void applyUrlIsAdzunasRedirectUrlVerbatim() {
        Map<String, Map<String, Object>> jobs = byId(fetch(source(NO_ROTATION), Set.of()));

        assertThat(jobs.get("adzuna-4876543210").get("applyUrl")).isEqualTo(
                "https://www.adzuna.com.br/land/ad/4876543210?se=Xy_Z1aB2&utm_medium=api"
                        + "&utm_source=a1b2c3d4&v=9F8E7D6C5B4A");
        assertThat(jobs.get("adzuna-4876542650").get("applyUrl")).isEqualTo(
                "https://www.adzuna.com.br/land/ad/4876542650?se=BvCxZz&utm_medium=api"
                        + "&utm_source=a1b2c3d4&v=1E2F3G4H5I6J");
    }

    @Test
    void aPredictedSalaryIsNotAStatedOne() {
        Map<String, Object> job = byId(fetch(source(NO_ROTATION), Set.of())).get("adzuna-4876543155");

        assertThat(job).doesNotContainKeys("salaryMin", "salaryMax", "salaryPeriod", "salaryBand");
        assertThat(job).containsEntry("contract", "pj")
                .containsEntry("state", "MG")
                .containsEntry("city", "Belo Horizonte")
                .doesNotContainKey("remote");
    }

    @Test
    void missingValuesStayAbsent() {
        Map<String, Object> job = byId(fetch(source(NO_ROTATION), Set.of())).get("adzuna-4876543099");

        // No company, no city below the state, no contract_type (contract_time alone never sets a
        // contract), no salary, no remote signal.
        assertThat(job).containsEntry("state", "PR")
                .doesNotContainKeys("company", "city", "contract", "salaryMin", "salaryMax",
                        "salaryPeriod", "salaryBand", "remote");
    }

    @Test
    void annualSalariesBecomeMonthlyWithTheYearlyPeriodKept() {
        Map<String, Map<String, Object>> jobs = byId(fetch(source(NO_ROTATION), Set.of()));

        // 18,000/yr = 1,500/mo; a minimum alone sets no maximum.
        assertThat(jobs.get("adzuna-4876542987")).containsEntry("salaryMin", 1500.0)
                .containsEntry("salaryPeriod", "ano").doesNotContainKey("salaryMax");
        // 19,800/yr = 1,650/mo; 21,600/yr = 1,800/mo.
        assertThat(jobs.get("adzuna-4876542777")).containsEntry("salaryMin", 1650.0)
                .containsEntry("salaryMax", 1800.0);
        // 36,000/yr = 3,000/mo.
        assertThat(jobs.get("adzuna-4876542650")).containsEntry("salaryMin", 3000.0);
        // 180,000/yr = 15,000/mo.
        assertThat(jobs.get("adzuna-4876542801")).containsEntry("salaryMin", 15000.0)
                .containsEntry("salaryMax", 20000.0);
        // TRA21: the band is the salary enricher's (DumAdzunaEnrichmentTest), never the mapper's.
        jobs.values().forEach(job -> assertThat(job).doesNotContainKey("salaryBand"));
    }

    @Test
    void remoteOnlyWhenStatedAndNeverForAHybridPosting() {
        Map<String, Map<String, Object>> jobs = byId(fetch(source(NO_ROTATION), Set.of()));

        assertThat(jobs.get("adzuna-4876542777")).containsEntry("remote", "remoto");
        // "Modelo híbrido ... dois de home office" is not a remote job.
        assertThat(jobs.get("adzuna-4876542801")).doesNotContainKey("remote");
        // Saying nothing about it is not "presencial".
        assertThat(jobs.get("adzuna-4876542650")).doesNotContainKey("remote");
    }

    @Test
    void stateAndCityFromTheLocationAreaWithAndWithoutAccents() {
        Map<String, Map<String, Object>> jobs = byId(fetch(source(NO_ROTATION), Set.of()));

        assertThat(jobs.get("adzuna-4876542987")).containsEntry("state", "CE")
                .containsEntry("city", "Fortaleza");
        assertThat(jobs.get("adzuna-4876542801")).containsEntry("state", "RJ")
                .containsEntry("city", "Rio de Janeiro");
        assertThat(jobs.get("adzuna-4876542650")).containsEntry("state", "RS")
                .containsEntry("city", "Porto Alegre");
        assertThat(jobs.get("adzuna-4876542777")).containsEntry("state", "BA");
    }

    @Test
    void urlSlugStripsAccentsAndPunctuation() {
        Map<String, Object> job = byId(fetch(source(NO_ROTATION), Set.of())).get("adzuna-4876542987");
        assertThat(job).containsEntry("url", "https://trabalharei.com.br/vaga/"
                + "tecnico-a-de-enfermagem-plantao-noturno-adzuna-4876542987/");

        Map<String, Object> other = byId(fetch(
                source("{\"categories\":[],\"siteUrl\":\"https://staging.example/vaga\"}"), Set.of()))
                .get("adzuna-4876542650");
        assertThat(other).containsEntry("url",
                "https://staging.example/vaga/motorista-entregador-adzuna-4876542650/");
    }

    // --- paging -------------------------------------------------------------------------------

    @Test
    void pagesNewestFirstUntilAnEmptyPage() {
        List<Map<String, Object>> jobs = fetch(source(NO_ROTATION), Set.of());

        assertThat(fetcher.pages()).containsExactly("newest:1", "newest:2", "newest:3");
        assertThat(jobs).hasSize(7);
        assertThat(jobs.getFirst()).containsEntry("id", "adzuna-4876543210");
    }

    @Test
    void sendsTheSearchParametersAndTheConfiguredMaxDaysOld() {
        fetch(source(NO_ROTATION), Set.of());
        Map<String, String> query = FixtureFetcher.query(fetcher.requests.getFirst());
        assertThat(fetcher.requests.getFirst().toString())
                .startsWith("https://api.adzuna.com/v1/api/jobs/br/search/1?");
        assertThat(query).containsEntry("app_id", "my-app-id")
                .containsEntry("app_key", "my-app-key")
                .containsEntry("results_per_page", "50")
                .containsEntry("sort_by", "date")
                .containsEntry("max_days_old", "30")
                .doesNotContainKeys("category", "what");

        fetcher.requests.clear();
        DumJobSource custom = source("{\"categories\":[],\"maxDaysOld\":7}");
        custom.setApiUrl("https://adzuna.proxy.local/search/");
        fetch(custom, Set.of());
        assertThat(fetcher.requests.getFirst().toString())
                .startsWith("https://adzuna.proxy.local/search/1?");
        assertThat(FixtureFetcher.query(fetcher.requests.getFirst()))
                .containsEntry("max_days_old", "7");
    }

    @Test
    void stopsAtThePageThatHoldsAnIdAnEarlierRunFetched() {
        List<Map<String, Object>> jobs = fetch(source(NO_ROTATION), Set.of("adzuna-4876542777"));

        // Page 2 holds a seen posting: page 3 is never asked for. The page is still kept whole.
        assertThat(fetcher.pages()).containsExactly("newest:1", "newest:2");
        assertThat(jobs).hasSize(7);

        fetcher.requests.clear();
        assertThat(fetch(source(NO_ROTATION), Set.of("adzuna-4876543099"))).hasSize(4);
        assertThat(fetcher.pages()).containsExactly("newest:1");
    }

    @Test
    void maxPagesBoundsAQuery() {
        fetch(source("{\"categories\":[],\"maxPages\":1}"), Set.of());
        assertThat(fetcher.pages()).containsExactly("newest:1");
    }

    // --- budget -------------------------------------------------------------------------------

    @Test
    void aBudgetRefusalEndsTheRunCleanlyWithWhatWasFetched() {
        DumJobSource source = source(null);
        DumJobCallBudget budget = DumJobCallBudget.builder(clock).cap(Window.MINUTE, 2).build();
        DumJobFetchContext context = new DumJobFetchContext(source, budget);

        List<Map<String, Object>> jobs = adapter.fetch(context);

        assertThat(jobs).hasSize(7);
        assertThat(fetcher.pages()).containsExactly("newest:1", "newest:2");
        // No category query ran, so the rotation did not move.
        assertThat(source.getCursor()).isNull();
        // D116: the stop reaches the run record, not only the log.
        assertThat(context.notes()).containsExactly("call budget exhausted at newest page 3");
    }

    @Test
    void adzunasPlanCapsAreTheDefaults() {
        assertThat(adapter.defaultCaps()).containsEntry(Window.MINUTE, 25)
                .containsEntry(Window.DAY, 250).containsEntry(Window.WEEK, 1000)
                .containsEntry(Window.MONTH, 2500);
        assertThat(adapter.id()).isEqualTo("adzuna");
    }

    @Test
    void theBudgetPersistsAcrossRuns() {
        InMemorySeenRepository seen = new InMemorySeenRepository();
        InMemoryCallRepository calls = new InMemoryCallRepository();
        DumJobSourceRepository sources = mock(DumJobSourceRepository.class);
        DumJobSource stored = source(null);
        stored.setCapPerDay(4);
        when(sources.findById(SOURCE_ID)).thenReturn(Optional.of(stored));

        // Run 1: newest pages 1-3 and the first category page use the 4 calls of the day; the
        // second category page is refused.
        List<String> first = run(stored, seen, calls, sources);
        assertThat(fetcher.pages()).containsExactly("newest:1", "newest:2", "newest:3",
                "it-jobs:1");
        assertThat(calls.calls(SOURCE_ID)).hasSize(4);
        assertThat(first).hasSize(8);

        // Run 2, an hour later, is a fresh budget seeded from the store: no call left today, so the
        // partner is not asked, and the stored postings are still reported.
        clock.advance(Duration.ofHours(1));
        List<String> second = run(stored, seen, calls, sources);
        assertThat(fetcher.requests).hasSize(4);
        assertThat(calls.calls(SOURCE_ID)).hasSize(4);
        assertThat(second).containsExactlyInAnyOrderElementsOf(first);

        // Run 3, a day later: the window rolled over and paging stops at the first seen page.
        clock.advance(Duration.ofDays(1));
        run(stored, seen, calls, sources);
        assertThat(fetcher.pages()).hasSize(6).endsWith("newest:1", "sales-jobs:1");
        assertThat(calls.calls(SOURCE_ID)).hasSize(6);
    }

    private List<String> run(DumJobSource stored, InMemorySeenRepository seen,
            InMemoryCallRepository calls, DumJobSourceRepository sources) {
        DumJobSource loaded = stored.toBuilder().build();
        return new DumJobsStructuredSource(loaded, DumJobsSchema.schema(), adapter, seen.repository,
                calls.repository, sources, clock).discover();
    }

    // --- category rotation --------------------------------------------------------------------

    @Test
    void theCategoryRotationAdvancesOneSlotPerRun() {
        DumJobSource source = source(
                "{\"categories\":[\"it-jobs\",\"sales-jobs\",\"admin-jobs\"],\"what\":\"vaga\"}");
        List<String> queried = new java.util.ArrayList<>();
        for (int run = 0; run < 4; run++) {
            fetcher.requests.clear();
            fetch(source, Set.of("adzuna-4876543210"));
            // Page 1 of the newest pass holds a seen id, then one category query.
            assertThat(fetcher.pages()).hasSize(2);
            Map<String, String> query = FixtureFetcher.query(fetcher.requests.get(1));
            assertThat(query).containsEntry("what", "vaga").containsEntry("sort_by", "date");
            assertThat(FixtureFetcher.query(fetcher.requests.getFirst())).doesNotContainKey("what");
            queried.add(query.get("category") + "->" + source.getCursor());
        }
        assertThat(queried).containsExactly("it-jobs->1", "sales-jobs->2", "admin-jobs->0",
                "it-jobs->1");
    }

    @Test
    void theCategoryQueryAddsPostingsTheNewestPassDidNotReach() {
        List<Map<String, Object>> jobs = fetch(source("{\"categories\":[\"it-jobs\"]}"), Set.of());

        assertThat(fetcher.pages()).containsExactly("newest:1", "newest:2", "newest:3", "it-jobs:1",
                "it-jobs:2");
        assertThat(jobs).hasSize(8);
        assertThat(byId(jobs).get("adzuna-4876531111")).containsEntry("state", "SC")
                .containsEntry("city", "Florianópolis").containsEntry("salaryMin", 18000.0);
    }

    @Test
    void theDefaultRotationIsUsedWhenNoneIsConfigured() {
        DumJobSource source = source(null);
        fetch(source, Set.of("adzuna-4876543210"));
        assertThat(fetcher.pages()).containsExactly("newest:1",
                DumAdzunaSettings.DEFAULT_CATEGORIES.getFirst() + ":1");
        assertThat(source.getCursor()).isEqualTo("1");
    }

    // --- failures -----------------------------------------------------------------------------

    @Test
    void missingCredentialsFailTheRun() {
        DumJobSource source = source(NO_ROTATION);
        source.setAppKey(null);
        assertThatThrownBy(() -> fetch(source, Set.of())).isInstanceOf(DumJobFetchException.class)
                .hasMessageContaining("appKey");
        assertThat(fetcher.requests).isEmpty();
    }

    @Test
    void aFailedFirstCallFailsTheRunButALaterOneKeepsWhatWasFetched() {
        fetcher.failing.add(1);
        assertThatThrownBy(() -> fetch(source(NO_ROTATION), Set.of()))
                .isInstanceOf(DumJobFetchException.class).hasMessageContaining("HTTP 500")
                .hasMessageNotContaining("my-app-key");

        fetcher.requests.clear();
        fetcher.failing.clear();
        fetcher.failing.add(2);
        assertThat(fetch(source(NO_ROTATION), Set.of())).hasSize(4);
    }

    @Test
    void malformedSettingsFailTheRun() {
        assertThatThrownBy(() -> fetch(source("{not json"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("settings");
    }

    // --- wiring -------------------------------------------------------------------------------

    @Test
    void theProviderRunsAdzunaSourcesOnThisAdapter() {
        DumJobSourceRepository sources = mock(DumJobSourceRepository.class);
        DumJobSource source = source(NO_ROTATION);
        when(sources.findByName("adzuna-br")).thenReturn(Optional.of(source));
        when(sources.findByEnabledTrue()).thenReturn(List.of(source));

        DumJobsSourceProvider provider = new DumJobsSourceProvider(sources,
                new InMemorySeenRepository().repository, new InMemoryCallRepository().repository,
                List.of(adapter), clock);

        assertThat(provider.sourceNames()).containsExactly("adzuna-br");
        assertThat(provider.resolve("adzuna-br")).hasValueSatisfying(
                s -> assertThat(s.discover()).hasSize(7));
    }
}
