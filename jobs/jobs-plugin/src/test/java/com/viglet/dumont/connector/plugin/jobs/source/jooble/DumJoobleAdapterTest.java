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

package com.viglet.dumont.connector.plugin.jobs.source.jooble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.viglet.dumont.connector.plugin.jobs.source.InMemoryCallRepository;
import com.viglet.dumont.connector.plugin.jobs.source.InMemorySeenRepository;
import com.viglet.dumont.connector.plugin.jobs.source.MutableClock;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobRequest;

class DumJoobleAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    static final String API_KEY = "a1b2c3d4-e5f6-4711-9abc-def012345678";
    private static final String NO_ID_LINK = "https://jooble.org/desc/away/no-id-918273?ckey=estagio&pos=4&p=1";

    private final MutableClock clock = new MutableClock(NOW);
    private final JoobleFixtureFetcher fetcher = new JoobleFixtureFetcher();
    private final DumJoobleAdapter adapter = new DumJoobleAdapter(fetcher);

    static DumJobSource source(String settings) {
        return DumJobSource.builder().id("src-jooble").name("jooble-br").sourceType("jooble")
                .apiKey(API_KEY).settings(settings).turSNSites(Set.of("trabalharei")).build();
    }

    private List<Map<String, Object>> fetch(DumJobSource source, Set<String> seen) {
        return adapter.fetch(new DumJobFetchContext(source, DumJobCallBudget.unlimited(clock),
                seen::contains));
    }

    private Map<String, Map<String, Object>> all() {
        return byId(fetch(source(null), Set.of()));
    }

    private static Map<String, Map<String, Object>> byId(List<Map<String, Object>> jobs) {
        return jobs.stream().collect(Collectors.toMap(j -> j.get("id").toString(),
                Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    // --- mapping ------------------------------------------------------------------------------

    @Test
    void mapsEveryFieldJoobleStates() {
        Map<String, Object> job = all().get("jooble-7316540912345678901");

        assertThat(job).containsOnlyKeys("id", "title", "text", "company", "city", "state", "remote",
                "contract", "salaryMin", "salaryMax", "salaryPeriod", "salaryBand", "source", "url",
                "applyUrl", "publishedAt", "modificationDate");
        assertThat(job)
                .containsEntry("title", "Desenvolvedor Java Pleno")
                .containsEntry("company", "Tech Brasil Ltda")
                .containsEntry("city", "Campinas")
                .containsEntry("state", "SP")
                .containsEntry("remote", "remoto")
                .containsEntry("contract", "clt")
                .containsEntry("salaryMin", 5000.0)
                .containsEntry("salaryMax", 7000.0)
                .containsEntry("salaryPeriod", "mes")
                .containsEntry("salaryBand", "3k-5k")
                .containsEntry("source", "jooble")
                .containsEntry("url", "https://trabalharei.com.br/vaga/"
                        + "desenvolvedor-java-pleno-jooble-7316540912345678901/")
                .containsEntry("publishedAt", "2026-10-10T09:15:00.0000000")
                .containsEntry("modificationDate", "2026-10-10T09:15:00.0000000");
        // HTML and entities stripped, nothing added to the short snippet.
        assertThat(job.get("text")).isEqualTo("...squad de pagamentos. Atuação 100% remota, com "
                + "encontros trimestrais em Campinas. Requisitos: Java 17, Spring Boot, PostgreSQL ...");
        assertThat(job).doesNotContainKeys("abstract", "seniority", "occupation", "expiresAt");
        assertThat(DumJobsSchema.FIELDS).containsAll(job.keySet());
    }

    @Test
    void applyUrlIsJoobleLinkVerbatim() {
        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs.get("jooble-7316540912345678901").get("applyUrl")).isEqualTo(
                "https://jooble.org/desc/7316540912345678901?ckey=desenvolvedor&rgn=-1&pos=1"
                        + "&groupId=40906&elckey=8820395104172531774&p=1&aq=-4127581936252271104"
                        + "&cid=3448&jobAge=2&relb=100&brelb=100&bscr=1&scr=1&iid=-8164925931028749313");
        assertThat(jobs.get("jooble--2208761450129834411").get("applyUrl")).isEqualTo(
                "https://jooble.org/desc/-2208761450129834411?ckey=vendedor&rgn=-1&pos=2"
                        + "&groupId=40906&elckey=8820395104172531774&p=1&aq=-4127581936252271104"
                        + "&cid=3448&jobAge=5");
    }

    @Test
    void aPostingWithoutAnIdGetsAStableHashOfItsLink() {
        String expected = DumJoobleMapper.id(null, NO_ID_LINK);
        assertThat(expected).matches("jooble-h[0-9a-f]{16}");
        assertThat(DumJoobleMapper.id(null, NO_ID_LINK)).isEqualTo(expected);
        assertThat(DumJoobleMapper.id(null, NO_ID_LINK + "&x=1")).isNotEqualTo(expected);
        assertThat(DumJoobleMapper.id(null, null)).isNull();

        Map<String, Object> job = all().get(expected);
        assertThat(job).containsEntry("title", "Estagiário Administrativo")
                .containsEntry("contract", "estagio").containsEntry("applyUrl", NO_ID_LINK)
                // "Brasil" names no state; a null company and a blank salary stay absent.
                .doesNotContainKeys("state", "city", "remote", "company", "salaryMin",
                        "salaryBand");
        assertThat(all()).containsKey(expected);
    }

    @Test
    void stateAndCityFromTheFreeTextLocation() {
        Map<String, Map<String, Object>> jobs = all();
        // "São Paulo - SP"
        assertThat(jobs.get("jooble--2208761450129834411")).containsEntry("state", "SP")
                .containsEntry("city", "São Paulo");
        // "Belo Horizonte, Minas Gerais"
        assertThat(jobs.get("jooble-5521938827364019922")).containsEntry("state", "MG")
                .containsEntry("city", "Belo Horizonte");
        // "Rio de Janeiro, RJ, Brasil"
        assertThat(jobs.get("jooble-1093847561029384756")).containsEntry("state", "RJ")
                .containsEntry("city", "Rio de Janeiro");
        // "Híbrido - Curitiba, PR": placed, and hybrid is not remote.
        assertThat(jobs.get("jooble-8837261540918273645")).containsEntry("state", "PR")
                .containsEntry("city", "Curitiba").doesNotContainKey("remote");
    }

    @Test
    void locationVariants() {
        assertThat(place("Campinas, SP")).containsEntry("city", "Campinas").containsEntry("state", "SP");
        assertThat(place("Curitiba/PR")).containsEntry("city", "Curitiba").containsEntry("state", "PR");
        assertThat(place("Vila Mariana, São Paulo, SP")).containsEntry("city", "São Paulo")
                .containsEntry("state", "SP");
        assertThat(place("Embu-Guaçu - SP")).containsEntry("city", "Embu-Guaçu");
        // A state alone sets no city: "São Paulo" may be either, and only a preceding part is a city.
        assertThat(place("São Paulo")).containsOnly(Map.entry("state", "SP"));
        assertThat(place("SP")).containsOnly(Map.entry("state", "SP"));
        assertThat(place("Brasil")).isEmpty();
        assertThat(place("Campinas")).isEmpty();
        assertThat(place(null)).isEmpty();
    }

    private static Map<String, Object> place(String location) {
        Map<String, Object> job = new LinkedHashMap<>();
        DumJoobleMapper.putPlace(job, location);
        return job;
    }

    @Test
    void aRemoteLocationIsRemoteWithNoPlace() {
        Map<String, Object> job = all().get("jooble-4410982231774520113");
        assertThat(job).containsEntry("remote", "remoto").containsEntry("contract", "pj")
                .doesNotContainKeys("city", "state");

        org.json.JSONObject homeOffice = new org.json.JSONObject()
                .put("id", "1").put("title", "Analista").put("location", "Home Office")
                .put("link", "https://jooble.org/desc/1").put("updated", "2026-10-01T00:00:00");
        assertThat(DumJoobleMapper.map(homeOffice, null)).containsEntry("remote", "remoto")
                .doesNotContainKeys("city", "state");
    }

    @Test
    void contractOnlyFromAnUnambiguousType() {
        assertThat(DumJoobleMapper.contract("CLT")).isEqualTo("clt");
        assertThat(DumJoobleMapper.contract("Efetivo")).isEqualTo("clt");
        assertThat(DumJoobleMapper.contract("CLT (Efetivo)")).isEqualTo("clt");
        assertThat(DumJoobleMapper.contract("CLT, Tempo integral")).isEqualTo("clt");
        assertThat(DumJoobleMapper.contract("PJ")).isEqualTo("pj");
        assertThat(DumJoobleMapper.contract("Pessoa Jurídica")).isEqualTo("pj");
        assertThat(DumJoobleMapper.contract("Estágio")).isEqualTo("estagio");
        assertThat(DumJoobleMapper.contract("Temporário")).isEqualTo("temporario");
        // Working time names no contract kind; two kinds are ambiguous; unknown words set nothing.
        assertThat(DumJoobleMapper.contract("Full-time")).isNull();
        assertThat(DumJoobleMapper.contract("Tempo integral")).isNull();
        assertThat(DumJoobleMapper.contract("Meio período")).isNull();
        assertThat(DumJoobleMapper.contract("CLT ou PJ")).isNull();
        assertThat(DumJoobleMapper.contract("Freelance")).isNull();
        assertThat(DumJoobleMapper.contract(null)).isNull();

        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs.get("jooble--2208761450129834411")).containsEntry("contract", "clt");
        assertThat(jobs.get("jooble-5521938827364019922")).containsEntry("contract", "temporario");
        assertThat(jobs.get("jooble-1093847561029384756")).doesNotContainKey("contract");
    }

    @Test
    void salaryOnlyWhenItClearlyStatesMonthlyBrl() {
        assertThat(salary("R$ 2.500 - R$ 3.000 por mês")).containsEntry("salaryMin", 2500.0)
                .containsEntry("salaryMax", 3000.0).containsEntry("salaryPeriod", "mes")
                .containsEntry("salaryBand", "1.5k-3k");
        assertThat(salary("R$ 3.000")).containsOnly(Map.entry("salaryMin", 3000.0),
                Map.entry("salaryPeriod", "mes"), Map.entry("salaryBand", "1.5k-3k"));
        assertThat(salary("R$ 1.412,00 mensal")).containsEntry("salaryMin", 1412.0)
                .containsEntry("salaryBand", "ate-1.5k");
        assertThat(salary("R$ 2.500,00 a R$ 3.000,00 /mês")).containsEntry("salaryMin", 2500.0)
                .containsEntry("salaryMax", 3000.0);
        assertThat(salary("R$4.000-R$6.000")).containsEntry("salaryMax", 6000.0);
        // Other periods, other currencies, vague or implausible text: left for the salary parser.
        assertThat(salary("R$ 18 por hora")).isEmpty();
        assertThat(salary("R$ 60.000 por ano")).isEmpty();
        assertThat(salary("R$ 150 por dia")).isEmpty();
        assertThat(salary("US$ 3,000")).isEmpty();
        assertThat(salary("3.000")).isEmpty();
        assertThat(salary("A combinar")).isEmpty();
        assertThat(salary("Até R$ 3.000")).isEmpty();
        assertThat(salary("R$ 3.000 + comissão")).isEmpty();
        assertThat(salary("R$ 25")).isEmpty();
        assertThat(salary("R$ 5.000 - R$ 3.000")).isEmpty();
        assertThat(salary("")).isEmpty();
        assertThat(salary(null)).isEmpty();

        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs.get("jooble--2208761450129834411")).containsEntry("salaryMin", 3000.0)
                .doesNotContainKey("salaryMax");
        assertThat(jobs.get("jooble-5521938827364019922")).containsEntry("salaryMin", 2500.0)
                .containsEntry("salaryMax", 3000.0);
        for (String id : List.of("jooble-4410982231774520113", "jooble-1093847561029384756",
                "jooble-8837261540918273645")) {
            assertThat(jobs.get(id)).doesNotContainKeys("salaryMin", "salaryMax", "salaryPeriod",
                    "salaryBand");
        }
    }

    private static Map<String, Object> salary(String text) {
        Map<String, Object> job = new LinkedHashMap<>();
        DumJoobleMapper.putSalary(job, text);
        return job;
    }

    @Test
    void datesAreJoobleUpdatedNeverTheRunClock() {
        Map<String, Object> first = all().get("jooble-1093847561029384756");
        clock.advance(java.time.Duration.ofDays(3));
        Map<String, Object> again = all().get("jooble-1093847561029384756");

        assertThat(first).containsEntry("publishedAt", "2026-10-06T08:00:00.0000000")
                .containsEntry("modificationDate", "2026-10-06T08:00:00.0000000");
        assertThat(again).isEqualTo(first);
    }

    // --- requests -----------------------------------------------------------------------------

    @Test
    void postsTheSearchAsJsonWithTheKeyInThePath() {
        fetch(source(null), Set.of());

        DumJobRequest first = fetcher.requests.getFirst();
        assertThat(first.method()).isEqualTo("POST");
        assertThat(first.uri().toString()).isEqualTo("https://jooble.org/api/" + API_KEY);
        assertThat(first.secrets()).containsExactly(API_KEY);
        assertThat(JoobleFixtureFetcher.values(first)).containsOnly(Map.entry("keywords", "emprego"),
                Map.entry("location", "Brasil"), Map.entry("page", "1"));
    }

    @Test
    void theMethodLocationRadiusSalaryAndApiUrlAreSettings() {
        DumJobSource source = source("{\"method\":\"get\",\"keywords\":[\"técnico de enfermagem\"],"
                + "\"location\":\"Fortaleza\",\"radius\":25,\"salary\":2000}");
        source.setApiUrl("https://jooble.proxy.local/api/");
        fetch(source, Set.of());

        DumJobRequest first = fetcher.requests.getFirst();
        assertThat(first.method()).isEqualTo("GET");
        assertThat(first.body()).isNull();
        assertThat(first.uri().toString()).startsWith("https://jooble.proxy.local/api/" + API_KEY + "?");
        assertThat(first.secrets()).containsExactly(API_KEY);
        assertThat(JoobleFixtureFetcher.values(first))
                .containsEntry("keywords", "técnico de enfermagem")
                .containsEntry("location", "Fortaleza").containsEntry("radius", "25")
                .containsEntry("salary", "2000").containsEntry("page", "1");

        assertThatThrownBy(() -> fetch(source("{\"method\":\"PUT\"}"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("method");
    }

    // --- paging -------------------------------------------------------------------------------

    @Test
    void pagesOneKeywordUntilAnEmptyPage() {
        List<Map<String, Object>> jobs = fetch(source(null), Set.of());

        assertThat(fetcher.pages()).containsExactly("emprego:1", "emprego:2", "emprego:3");
        assertThat(jobs).hasSize(7);
        assertThat(jobs.getFirst()).containsEntry("id", "jooble-7316540912345678901");
    }

    @Test
    void stopsAtThePageThatHoldsAnIdAnEarlierRunFetched() {
        List<Map<String, Object>> jobs = fetch(source(null), Set.of("jooble--2208761450129834411"));

        // Page 1 holds a seen posting: page 2 is never asked for. The page is still kept whole.
        assertThat(fetcher.pages()).containsExactly("emprego:1");
        assertThat(jobs).hasSize(4);

        fetcher.requests.clear();
        assertThat(fetch(source(null), Set.of("jooble-1093847561029384756"))).hasSize(7);
        assertThat(fetcher.pages()).containsExactly("emprego:1", "emprego:2");
    }

    @Test
    void maxPagesBoundsARun() {
        fetch(source("{\"maxPages\":1}"), Set.of());
        assertThat(fetcher.pages()).containsExactly("emprego:1");
    }

    // --- keyword rotation ---------------------------------------------------------------------

    @Test
    void theKeywordRotationAdvancesOneSlotPerRun() {
        DumJobSource source = source("{\"keywords\":[\"vendedor\",\"motorista\",\"atendente\"]}");
        List<String> queried = new ArrayList<>();
        for (int run = 0; run < 4; run++) {
            fetcher.requests.clear();
            fetch(source, Set.of("jooble-7316540912345678901"));
            assertThat(fetcher.requests).hasSize(1);
            queried.add(fetcher.pages().getFirst() + "->" + source.getCursor());
        }
        assertThat(queried).containsExactly("vendedor:1->1", "motorista:1->2", "atendente:1->0",
                "vendedor:1->1");
    }

    @Test
    void theDefaultKeywordsAreABroadPtBrList() {
        assertThat(DumJoobleSettings.parse(null).keywords()).containsExactly("emprego", "vagas",
                "estágio", "auxiliar", "vendedor", "motorista", "desenvolvedor", "atendente");
        // An empty list cannot search Jooble: the defaults apply.
        assertThat(DumJoobleSettings.parse("{\"keywords\":[]}").keywords())
                .isEqualTo(DumJoobleSettings.DEFAULT_KEYWORDS);
        DumJobSource source = source(null);
        source.setCursor("7");
        fetch(source, Set.of("jooble-7316540912345678901"));
        assertThat(fetcher.pages()).containsExactly("atendente:1");
        assertThat(source.getCursor()).isEqualTo("0");
    }

    // --- budget -------------------------------------------------------------------------------

    @Test
    void aBudgetRefusalEndsTheRunWithWhatWasFetched() {
        DumJobSource source = source(null);
        DumJobCallBudget budget = DumJobCallBudget.builder(clock).cap(Window.MINUTE, 1).build();

        List<Map<String, Object>> jobs = adapter.fetch(new DumJobFetchContext(source, budget));

        assertThat(jobs).hasSize(4);
        assertThat(fetcher.pages()).containsExactly("emprego:1");
        // A call was made, so the rotation moved.
        assertThat(source.getCursor()).isEqualTo("1");
    }

    @Test
    void aRunTheBudgetRefusesOutrightKeepsTheRotationSlot() {
        DumJobSource source = source(null);
        source.setCursor("3");
        DumJobCallBudget budget = DumJobCallBudget.builder(clock).cap(Window.DAY, 1).build();
        budget.tryAcquire();

        assertThat(adapter.fetch(new DumJobFetchContext(source, budget))).isEmpty();
        assertThat(fetcher.requests).isEmpty();
        assertThat(source.getCursor()).isEqualTo("3");
    }

    @Test
    void conservativeDefaultCaps() {
        assertThat(adapter.defaultCaps()).containsEntry(Window.MINUTE, 10)
                .containsEntry(Window.DAY, 500).containsEntry(Window.WEEK, 3000)
                .containsEntry(Window.MONTH, 10000);
        assertThat(adapter.id()).isEqualTo("jooble");

        // A source's own cap overrides the default for its window.
        DumJobSource source = source(null);
        source.setCapPerMinute(2);
        DumJobCallBudget budget = DumJobCallBudget.builder(clock)
                .caps(source, adapter.defaultCaps()).build();
        assertThat(budget.remaining(Window.MINUTE)).hasValue(2);
        assertThat(budget.remaining(Window.DAY)).hasValue(500);
    }

    // --- failures -----------------------------------------------------------------------------

    @Test
    void aMissingApiKeyFailsTheRun() {
        DumJobSource source = source(null);
        source.setApiKey(" ");
        assertThatThrownBy(() -> fetch(source, Set.of())).isInstanceOf(DumJobFetchException.class)
                .hasMessageContaining("apiKey");
        assertThat(fetcher.requests).isEmpty();
    }

    @Test
    void aFailedFirstCallFailsTheRunButALaterOneKeepsWhatWasFetched() {
        fetcher.failing.add(1);
        assertThatThrownBy(() -> fetch(source(null), Set.of()))
                .isInstanceOf(DumJobFetchException.class).hasMessageContaining("HTTP 500");

        fetcher.requests.clear();
        fetcher.failing.clear();
        fetcher.failing.add(2);
        assertThat(fetch(source(null), Set.of())).hasSize(4);
    }

    @Test
    void malformedSettingsFailTheRun() {
        assertThatThrownBy(() -> fetch(source("{not json"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("settings");
    }

    // --- wiring -------------------------------------------------------------------------------

    @Test
    void theProviderRunsJoobleSourcesOnThisAdapter() {
        DumJobSourceRepository sources = mock(DumJobSourceRepository.class);
        DumJobSource source = source(null);
        when(sources.findByName("jooble-br")).thenReturn(Optional.of(source));
        when(sources.findByEnabledTrue()).thenReturn(List.of(source));

        DumJobsSourceProvider provider = new DumJobsSourceProvider(sources,
                new InMemorySeenRepository().repository, new InMemoryCallRepository().repository,
                List.of(adapter), clock);

        assertThat(provider.sourceNames()).containsExactly("jooble-br");
        assertThat(provider.resolve("jooble-br")).hasValueSatisfying(
                s -> assertThat(s.discover()).hasSize(7));
    }
}
