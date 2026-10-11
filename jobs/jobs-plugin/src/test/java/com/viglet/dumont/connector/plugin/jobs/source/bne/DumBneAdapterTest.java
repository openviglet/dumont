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

package com.viglet.dumont.connector.plugin.jobs.source.bne;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSeen;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget.Window;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobFetchContext;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobsSourceProvider;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobsStructuredSource;
import com.viglet.dumont.connector.plugin.jobs.source.InMemoryCallRepository;
import com.viglet.dumont.connector.plugin.jobs.source.InMemorySeenRepository;
import com.viglet.dumont.connector.plugin.jobs.source.MutableClock;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;

class DumBneAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00.123456Z");
    private static final String NOW_TEXT = "2026-10-10T12:00:00Z";
    private static final String LINK = "https://www.bne.com.br/vaga-de-emprego-na-area-";

    private final MutableClock clock = new MutableClock(NOW);
    private final BneFixtureFetcher fetcher = new BneFixtureFetcher();
    private final DumBneAdapter adapter = new DumBneAdapter(fetcher, clock);

    static DumJobSource source(String settings) {
        return DumJobSource.builder().id("src-bne").name("bne-br").sourceType("bne")
                .settings(settings).turSNSites(Set.of("trabalharei")).build();
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
    void mapsEveryFieldBneStates() {
        Map<String, Object> job = all().get("bne-4410001");

        assertThat(job).containsOnlyKeys("id", "title", "text", "city", "state", "remote", "source",
                "url", "applyUrl", "publishedAt", "modificationDate");
        assertThat(job)
                .containsEntry("title", "Desenvolvedor Java Pleno")
                .containsEntry("text", "Atuação 100% home office. Requisitos: Java 17, Spring Boot "
                        + "e PostgreSQL.")
                .containsEntry("city", "São Paulo")
                .containsEntry("state", "SP")
                .containsEntry("remote", "remoto")
                .containsEntry("source", "bne")
                .containsEntry("url", "https://trabalharei.com.br/vaga/desenvolvedor-java-pleno-bne-4410001/")
                .containsEntry("applyUrl", LINK + "informatica-em-sao-paulo-sp/desenvolvedor-java-pleno/a1b2c3d4")
                .containsEntry("publishedAt", NOW_TEXT)
                .containsEntry("modificationDate", NOW_TEXT);
        // BNE states none of these: they stay absent, never a placeholder.
        assertThat(job).doesNotContainKeys("company", "contract", "occupation", "seniority",
                "salaryMin", "salaryMax", "salaryPeriod", "salaryBand", "abstract", "expiresAt");
        assertThat(DumJobsSchema.FIELDS).containsAll(job.keySet());
    }

    @Test
    void anEmptyDescriptionLeavesTextAbsentAndNothingIsInvented() {
        Map<String, Map<String, Object>> jobs = all();
        // "" and null descriptions: no text, and not the plugin's "<title> em <city/uf> área <area>".
        for (String id : List.of("bne-4410002", "bne-4410004")) {
            assertThat(jobs.get(id)).doesNotContainKey("text");
            assertThat(jobs.get(id).values()).noneMatch(v -> v.toString().contains(" área "));
        }
        // HTML in a description is reduced to plain text.
        assertThat(jobs.get("bne-4410005")).containsEntry("text",
                "Vendas na região metropolitana. Veículo próprio.");
    }

    @Test
    void stateOnlyWhenItIsAUfAndAnInvalidOneIsDropped() {
        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs.get("bne-4410002")).containsEntry("state", "MG")
                .containsEntry("city", "Belo  Horizonte");
        assertThat(jobs.get("bne-4410003")).doesNotContainKey("state")
                .containsEntry("city", "Embu-Guaçu");
        assertThat(jobs.get("bne-4410006")).containsEntry("state", "PE");
    }

    @Test
    void anEntryWithoutAnIdIsDropped() {
        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs).containsOnlyKeys("bne-4410001", "bne-4410002", "bne-4410003", "bne-4410004",
                "bne-4410005", "bne-4410006");
        assertThat(DumBneMapper.map(new JSONObject().put("Title", "Sem Id"), null, NOW)).isEmpty();
    }

    @Test
    void remoteOnlyWhenTheTextSaysSoAndNeverForHybrid() {
        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs.get("bne-4410001")).containsEntry("remote", "remoto");
        // "Híbrido ... dois dias remoto": hybrid, so not remote.
        assertThat(jobs.get("bne-4410006")).doesNotContainKey("remote");
        assertThat(jobs.get("bne-4410005")).doesNotContainKey("remote");
    }

    @Test
    void theMapperSetsNoContract() {
        // TRA21: BNE has no contract field; the contract enricher reads title and description
        // (DumBneEnrichmentTest), so the mapper never infers one.
        all().values().forEach(job -> assertThat(job).doesNotContainKey("contract"));
    }

    @Test
    void applyLinksOfTheFixturesMatchThePlugin() {
        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs.get("bne-4410002").get("applyUrl")).isEqualTo(LINK
                + "administraçao-em-belo-horizonte-mg/estagio-em-administraçao/e5f6a7b8");
        assertThat(jobs.get("bne-4410003").get("applyUrl")).isEqualTo(LINK
                + "produçao-/-industrial-em-embu-guaçu-xx/auxiliar-de-produçao-temporario/c9d0e1f2");
        assertThat(jobs.get("bne-4410004").get("applyUrl")).isEqualTo(LINK
                + "atendimento-ao-cliente-em-sao-jose-dos-campos-sp/-jovem-aprendiz-/0a1b2c3d");
        assertThat(jobs.get("bne-4410005").get("applyUrl")).isEqualTo(LINK
                + "comercial-vendas-em-curitiba-pr/vendedor-externo/f00dbabe");
        // The plugin lower-cases State as sent, even a full name.
        assertThat(jobs.get("bne-4410006").get("applyUrl")).isEqualTo(LINK
                + "tecnologia-em-recife-pernambuco/analista-de-suporte-hibrido/beef1234");
    }

    @Test
    void pluginSlugReplacesOnlyThePluginsAccentsAndRunsOfSpacesOrHyphens() {
        // Every accent of TirarAcentos, lower and upper case.
        assertThat(DumBneMapper.pluginSlug("áàãâä ÁÀÃÂÄ éèêë ÉÈÊË íìîï ÍÌÎÏ óòõôö ÓÒÕÔÖ úùûü ÚÙÛÜ ñ Ñ"))
                .isEqualTo("aaaaa-aaaaa-eeee-eeee-iiii-iiii-ooooo-ooooo-uuuu-uuuu-n-n");
        // Runs of spaces and hyphens, mixed, become one "-"; leading/trailing ones are kept.
        assertThat(DumBneMapper.pluginSlug("Auxiliar   de -- Limpeza")).isEqualTo("auxiliar-de-limpeza");
        assertThat(DumBneMapper.pluginSlug("Técnico - - Enfermagem")).isEqualTo("tecnico-enfermagem");
        assertThat(DumBneMapper.pluginSlug(" Vendas ")).isEqualTo("-vendas-");
        // Upper case is lowered after the replacement, ç (not in the plugin's list) included.
        assertThat(DumBneMapper.pluginSlug("AÇOUGUEIRO")).isEqualTo("açougueiro");
        assertThat(DumBneMapper.pluginSlug("Coração")).isEqualTo("coraçao");
        // Anything else stays as it is: tabs, slashes, parentheses, dots.
        assertThat(DumBneMapper.pluginSlug("Motorista (CNH D)/Entregas")).isEqualTo("motorista-(cnh-d)/entregas");
        assertThat(DumBneMapper.pluginSlug("Eng.\tCivil")).isEqualTo("eng.\tcivil");
        assertThat(DumBneMapper.pluginSlug("")).isEmpty();
    }

    @Test
    void applyLinkFromRawValues() {
        JSONObject vaga = new JSONObject().put("Id", 1).put("Idf_Vaga", "AbC-12")
                .put("Title", "Operador de Empilhadeira -  Noturno").put("Area", "Logística")
                .put("City", "São Bernardo do Campo").put("State", "SP");
        assertThat(DumBneMapper.applyUrl(vaga)).isEqualTo(LINK
                + "logistica-em-sao-bernardo-do-campo-sp/operador-de-empilhadeira-noturno/abc-12");

        // Missing parts read as "" (as PHP reads null); only a missing Idf_Vaga drops the link.
        JSONObject sparse = new JSONObject().put("Id", 2).put("Idf_Vaga", "Z9").put("Title", "Caixa");
        assertThat(DumBneMapper.applyUrl(sparse)).isEqualTo(LINK + "-em--/caixa/z9");
        assertThat(DumBneMapper.applyUrl(new JSONObject().put("Id", 3).put("Title", "Caixa"))).isNull();
        assertThat(DumBneMapper.map(new JSONObject().put("Id", 3).put("Title", "Caixa"), null, NOW))
                .doesNotContainKey("applyUrl").containsEntry("id", "bne-3");
    }

    // --- publishedAt: the first time the posting was seen ----------------------------------------

    @Test
    void publishedAtIsTheFirstSeenTimeFromTheContext() {
        Instant earlier = Instant.parse("2026-09-01T08:30:00.987Z");
        List<Map<String, Object>> jobs = adapter.fetch(new DumJobFetchContext(source(null),
                DumJobCallBudget.unlimited(clock), id -> false,
                id -> id.equals("bne-4410001") ? earlier : null));
        Map<String, Map<String, Object>> byId = byId(jobs);

        assertThat(byId.get("bne-4410001")).containsEntry("publishedAt", "2026-09-01T08:30:00Z")
                .containsEntry("modificationDate", "2026-09-01T08:30:00Z");
        // No history from the context: this run's time, to the second.
        assertThat(byId.get("bne-4410002")).containsEntry("publishedAt", NOW_TEXT);
    }

    @Test
    void publishedAtAndThePayloadStayStableAcrossRuns() {
        InMemorySeenRepository seen = new InMemorySeenRepository();
        InMemoryCallRepository calls = new InMemoryCallRepository();
        DumJobSourceRepository sources = mock(DumJobSourceRepository.class);
        DumJobSource stored = source(null);
        when(sources.findById("src-bne")).thenReturn(Optional.of(stored));
        Function<DumJobSource, DumJobsStructuredSource> run = s -> new DumJobsStructuredSource(s,
                        DumJobsSchema.schema(), adapter, seen.repository, calls.repository, sources, clock);

        run.apply(source(null)).discover();
        DumJobSeen first = seen.find("src-bne", "bne-4410001").orElseThrow();
        String payload = first.getPayload();
        assertThat(first.getFirstSeenAt()).isEqualTo(NOW);
        assertThat(new JSONObject(payload).getString("publishedAt")).isEqualTo(NOW_TEXT);
        // TRA24: BNE is undated, so a posting lives 30 days past the run that last fetched it.
        assertThat(first.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));

        clock.advance(Duration.ofDays(2).plusMinutes(7));
        run.apply(source(null)).discover();

        DumJobSeen again = seen.find("src-bne", "bne-4410001").orElseThrow();
        assertThat(again.getFirstSeenAt()).isEqualTo(NOW);
        assertThat(again.getLastSeenAt()).isEqualTo(clock.instant());
        assertThat(again.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(30)));
        assertThat(new JSONObject(again.getPayload()).similar(new JSONObject(payload))).isTrue();
        assertThat(new JSONObject(again.getPayload()).getString("modificationDate")).isEqualTo(NOW_TEXT);
    }

    // --- requests -----------------------------------------------------------------------------

    @Test
    void getsTheListWithNoKeyAndNoHeader() {
        fetch(source(null), Set.of());

        assertThat(fetcher.requests.getFirst().toString()).isEqualTo(
                "https://plugin-api.vagas.bne.com.br/api/v1/Lists/Jobs?Page=1&ItemsPerPage=50");
        assertThat(fetcher.headers).allMatch(Map::isEmpty);
    }

    @Test
    void theSourceApiUrlWinsOverTheSettingsOne() {
        DumJobSource source = source("{\"apiUrl\":\"https://bne.settings.local/Lists/Jobs/\","
                + "\"itemsPerPage\":20,\"maxPages\":1}");
        fetch(source, Set.of());
        assertThat(fetcher.requests.getFirst().toString())
                .isEqualTo("https://bne.settings.local/Lists/Jobs?Page=1&ItemsPerPage=20");

        fetcher.requests.clear();
        source.setApiUrl("https://bne.proxy.local/api/v1/Lists/Jobs");
        fetch(source, Set.of());
        assertThat(fetcher.requests.getFirst().toString())
                .isEqualTo("https://bne.proxy.local/api/v1/Lists/Jobs?Page=1&ItemsPerPage=20");
    }

    // --- paging -------------------------------------------------------------------------------

    @Test
    void pagesTheListThenOneUfUntilTotalPages() {
        DumJobSource source = source(null);
        List<Map<String, Object>> jobs = fetch(source, Set.of());

        // TotalPages is 2: page 3 is never asked for, in the unfiltered list nor in the UF query.
        assertThat(fetcher.pages()).containsExactly("all:1", "all:2", "AC:1", "AC:2");
        assertThat(jobs).hasSize(6);
        assertThat(source.getCursor()).isEqualTo("1");
    }

    @Test
    void stopsAtAnEmptyPage() {
        fetcher.overrides.put("2", BneFixtureFetcher.read("page-2").replace("\"TotalPages\": 2",
                "\"TotalPages\": 9"));
        fetch(source("{\"states\":[\"SP\"]}"), Set.of());

        assertThat(fetcher.pages()).containsExactly("all:1", "all:2", "all:3", "SP:1", "SP:2", "SP:3");
    }

    @Test
    void stopsAtMaxPages() {
        fetch(source("{\"maxPages\":1}"), Set.of());
        assertThat(fetcher.pages()).containsExactly("all:1", "AC:1");
    }

    @Test
    void stopsOnlyAtAPageWhoseEveryPostingWasSeen() {
        // One seen posting on page 1 is not enough: BNE has no date order to trust.
        fetch(source(null), Set.of("bne-4410001"));
        assertThat(fetcher.pages()).containsExactly("all:1", "all:2", "AC:1", "AC:2");

        fetcher.requests.clear();
        List<Map<String, Object>> jobs = fetch(source(null),
                Set.of("bne-4410001", "bne-4410002", "bne-4410003", "bne-4410004"));
        // Page 1 is all seen: kept whole (refreshed), page 2 never asked for.
        assertThat(fetcher.pages()).containsExactly("all:1", "AC:1");
        assertThat(jobs).hasSize(4);
    }

    @Test
    void aFailureBeforeAnythingWasFetchedThrowsAndAfterKeepsWhatItHas() {
        fetcher.failing.add(1);
        assertThatThrownBy(() -> fetch(source(null), Set.of())).isInstanceOf(DumJobFetchException.class);

        fetcher.requests.clear();
        fetcher.failing.clear();
        fetcher.failing.add(2);
        fetcher.failing.add(4);
        // Page 2 of the list fails: the list query ends, the UF query still runs (as Adzuna's
        // category query does) and its own failure ends it with what the run has.
        assertThat(fetch(source(null), Set.of())).hasSize(4);
        assertThat(fetcher.pages()).containsExactly("all:1", "all:2", "AC:1", "AC:2");
    }

    @Test
    void aResponseThatIsNotJsonFailsTheRun() {
        fetcher.overrides.put("1", "<html>erro</html>");
        assertThatThrownBy(() -> fetch(source(null), Set.of())).isInstanceOf(DumJobFetchException.class)
                .hasMessageContaining("BNE");
        assertThat(DumBneAdapter.page("{}").vagas()).isEmpty();
        assertThat(DumBneAdapter.page("{\"data\":null}").totalPages()).isNull();
    }

    // --- UF rotation --------------------------------------------------------------------------

    @Test
    void theUfRotationWalksAll27OneSlotPerRun() {
        assertThat(DumBneSettings.parse(null).states()).hasSize(27).isEqualTo(DumJobNormalizer.UFS)
                .allMatch(uf -> uf.equals(DumJobNormalizer.uf(uf)));
        assertThat(DumJobNormalizer.UFS).startsWith("AC", "AL", "AM", "AP").endsWith("SP", "TO");

        DumJobSource source = source("{\"maxPages\":1}");
        List<String> queried = new ArrayList<>();
        for (int run = 0; run < 28; run++) {
            fetcher.requests.clear();
            fetch(source, Set.of());
            queried.add(fetcher.pages().get(1));
        }
        assertThat(queried.subList(0, 3)).containsExactly("AC:1", "AL:1", "AM:1");
        assertThat(queried.get(26)).isEqualTo("TO:1");
        assertThat(queried.get(27)).isEqualTo("AC:1");
        assertThat(queried.stream().distinct()).hasSize(27);
    }

    @Test
    void theStatesSettingReplacesTheRotation() {
        DumJobSource source = source("{\"maxPages\":1,\"states\":[\"rj\",\"São Paulo\",\"RJ\"]}");
        List<String> queried = new ArrayList<>();
        for (int run = 0; run < 3; run++) {
            fetcher.requests.clear();
            fetch(source, Set.of());
            queried.add(fetcher.pages().get(1) + "->" + source.getCursor());
        }
        assertThat(queried).containsExactly("RJ:1->1", "SP:1->0", "RJ:1->1");

        assertThatThrownBy(() -> DumBneSettings.parse("{\"states\":[\"XX\"]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("XX");
        assertThatThrownBy(() -> DumBneSettings.parse("not json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(DumBneSettings.parse("{\"states\":[]}").states()).hasSize(27);
    }

    // --- budget -------------------------------------------------------------------------------

    @Test
    void aBudgetRefusalEndsTheRunAndKeepsTheUfSlot() {
        DumJobSource source = source(null);
        source.setCursor("5");
        DumJobCallBudget budget = DumJobCallBudget.builder(clock).cap(Window.MINUTE, 2).build();
        DumJobFetchContext context = new DumJobFetchContext(source, budget);

        List<Map<String, Object>> jobs = adapter.fetch(context);

        // Both calls went to the unfiltered list; the UF query never ran, so the rotation stays.
        assertThat(fetcher.pages()).containsExactly("all:1", "all:2");
        assertThat(context.notes()).singleElement().asString().startsWith("call budget exhausted at ");
        assertThat(jobs).hasSize(6);
        assertThat(source.getCursor()).isEqualTo("5");
    }

    @Test
    void theUfSlotMovesOnceTheUfQueryMadeACall() {
        DumJobSource source = source(null);
        DumJobCallBudget budget = DumJobCallBudget.builder(clock).cap(Window.MINUTE, 3).build();

        adapter.fetch(new DumJobFetchContext(source, budget));

        assertThat(fetcher.pages()).containsExactly("all:1", "all:2", "AC:1");
        assertThat(source.getCursor()).isEqualTo("1");
    }

    @Test
    void conservativeDefaultCapsAndTheAdapterIsPickedForBneSources() {
        assertThat(adapter.defaultCaps()).containsOnly(Map.entry(Window.MINUTE, 10),
                Map.entry(Window.DAY, 500), Map.entry(Window.WEEK, 3000), Map.entry(Window.MONTH, 10000));
        assertThat(adapter.id()).isEqualTo("bne");

        DumJobSource source = source(null);
        source.setCapPerDay(40);
        DumJobCallBudget budget = DumJobCallBudget.builder(clock).caps(source, adapter.defaultCaps())
                .build();
        assertThat(budget.remaining(Window.DAY)).hasValue(40);
        assertThat(budget.remaining(Window.MINUTE)).hasValue(10);
    }

    @Test
    void theProviderRunsBneSourcesOnThisAdapter() {
        DumJobSourceRepository sources = mock(DumJobSourceRepository.class);
        DumJobSource source = source(null);
        when(sources.findByName("bne-br")).thenReturn(Optional.of(source));
        when(sources.findByEnabledTrue()).thenReturn(List.of(source));

        DumJobsSourceProvider provider = new DumJobsSourceProvider(sources,
                new InMemorySeenRepository().repository, new InMemoryCallRepository().repository,
                List.of(adapter), clock);

        assertThat(provider.sourceNames()).containsExactly("bne-br");
        assertThat(provider.resolve("bne-br")).hasValueSatisfying(
                s -> assertThat(s.discover()).hasSize(6));
    }
}
