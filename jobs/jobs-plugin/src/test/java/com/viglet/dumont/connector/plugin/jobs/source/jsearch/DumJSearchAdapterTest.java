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

package com.viglet.dumont.connector.plugin.jobs.source.jsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class DumJSearchAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    static final String API_KEY = "9f8e7d6c5bmsh4a3b2c1d0e9f8a7p1c2d3ejsn4f5a6b7c8d9e";

    /** The fixtures' job ids. */
    static final String JAVA = "aBcD3fGh1JkLmN0pQrStUvWxYz-_12345AbCdEfGhIjKlMnOpQrStUvWxYz==";
    static final String DADOS = "Zx9Yw8Vu7Ts6Rq5Po4Nm3Lk2Ji1Hg0Fe-_98765ZyXwVuTsRqPoNmLkJiHgFe==";
    static final String ESTAGIO = "Qw3Er5Ty7Ui9Op1As3Df5Gh7Jk9Lz-_ESTAGIO2026RJqwertyuiop==";
    static final String US = "UsOnLy-NotBrazil-Austin-TX-0001-Zm9vYmFyYmF6cXV4==";
    static final String ATENDENTE = "Mg-BH-Atendente-Meio-Periodo-cGFydHRpbWVfdXNk==";
    static final String SUPORTE = "Pr-Curitiba-Analista-Contractor-Y29udHJhY3Rvcg==";
    static final String FRONT = "Remote-NoState-Intern-Or-Contractor-YW1iaWd1b3Vz==";
    static final String PT = "Pt-Lisboa-Not-Brazil-bGlzYm9h==";

    private final MutableClock clock = new MutableClock(NOW);
    private final JSearchFixtureFetcher fetcher = new JSearchFixtureFetcher();
    private final DumJSearchAdapter adapter = new DumJSearchAdapter(fetcher);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(DumJSearchAdapter.class);

    @BeforeEach
    void captureLogs() {
        appender.start();
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    static DumJobSource source(String settings) {
        return DumJobSource.builder().id("src-jsearch").name("jsearch-br").sourceType("jsearch")
                .apiKey(API_KEY).settings(settings).turSNSites(Set.of("trabalharei")).build();
    }

    /** {@code jsearch-} plus the first 16 hex of SHA-256, computed here independently of the code. */
    static String id(String jobId) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(jobId.getBytes(StandardCharsets.UTF_8));
        return "jsearch-" + HexFormat.of().formatHex(digest).substring(0, 16);
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

    private List<String> logLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private DumJobCallBudget budget(DumJobSource source, List<Instant> prior) {
        return DumJobCallBudget.builder(clock).caps(source, adapter.defaultCaps()).priorCalls(prior)
                .build();
    }

    // --- mapping ------------------------------------------------------------------------------

    @Test
    void mapsEveryFieldJSearchStates() throws Exception {
        String id = id(JAVA);
        Map<String, Object> job = all().get(id);

        assertThat(job).containsOnlyKeys("id", "title", "text", "company", "city", "state", "locationRaw", "remote",
                "salaryMin", "salaryMax", "salaryPeriod", "source", "url", "applyUrl",
                "publishedAt", "modificationDate", "expiresAt");
        assertThat(job)
                .containsEntry("title", "Desenvolvedor Java Pleno")
                .containsEntry("text", "Buscamos Desenvolvedor Java Pleno para o squad de pagamentos. "
                        + "Requisitos: • Java 17 e Spring Boot • PostgreSQL Trabalho 100% remoto.")
                .containsEntry("company", "Tech Brasil Ltda")
                .containsEntry("city", "São Paulo")
                .containsEntry("state", "SP")
                .containsEntry("remote", "remoto")
                .containsEntry("salaryMin", 5000.0)
                .containsEntry("salaryMax", 7000.0)
                .containsEntry("salaryPeriod", "mes")
                .containsEntry("source", "jsearch")
                .containsEntry("url", "https://trabalharei.com.br/vaga/desenvolvedor-java-pleno-" + id + "/")
                .containsEntry("publishedAt", "2026-10-08T12:00:00.000Z")
                .containsEntry("modificationDate", "2026-10-08T12:00:00.000Z")
                .containsEntry("expiresAt", "2026-11-07T12:00:00.000Z");
        // FULLTIME is working time: no contract.
        // TRA21: the band and the seniority are the enrichers', never the mapper's.
        assertThat(job).doesNotContainKeys("contract", "abstract", "seniority", "occupation",
                "salaryBand");
        // TRA20: the partner's place text rides along for the location enricher; it is a record
        // key, not a schema field.
        assertThat(job).containsEntry("locationRaw", "São Paulo, SP");
        assertThat(DumJobsSchema.FIELDS).containsAll(job.keySet().stream()
                .filter(key -> !key.equals("locationRaw")).toList());
    }

    @Test
    void theIdIsAStableShortHashOfTheJobId() throws Exception {
        String expected = id(JAVA);
        assertThat(expected).matches("jsearch-[0-9a-f]{16}");
        assertThat(DumJSearchMapper.id(JAVA)).isEqualTo(expected);
        assertThat(DumJSearchMapper.id(JAVA)).isEqualTo(DumJSearchMapper.id(JAVA));
        assertThat(DumJSearchMapper.id(JAVA + "x")).isNotEqualTo(expected);
        assertThat(DumJSearchMapper.id(null)).isNull();
        // The same posting fetched again keeps its id.
        assertThat(all().keySet()).isEqualTo(all().keySet()).contains(expected);

        JSONObject noId = new JSONObject().put("job_title", "Vendedor").put("job_country", "BR");
        assertThat(DumJSearchMapper.map(noId, null)).isEmpty();
    }

    @Test
    void onlyBrazilianPostingsAreKept() throws Exception {
        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs).hasSize(6).doesNotContainKeys(id(US), id(PT));
        assertThat(jobs.keySet()).containsExactly(id(JAVA), id(DADOS), id(ESTAGIO), id(ATENDENTE),
                id(SUPORTE), id(FRONT));

        JSONObject noCountry = new JSONObject().put("job_id", "x").put("job_title", "Vendedor");
        assertThat(DumJSearchMapper.map(noCountry, null)).isEmpty();
        assertThat(DumJSearchMapper.isBrazil("br")).isTrue();
        assertThat(DumJSearchMapper.isBrazil("Brasil")).isTrue();
        assertThat(DumJSearchMapper.isBrazil("PT")).isFalse();
        assertThat(DumJSearchMapper.isBrazil(null)).isFalse();
        assertThat(logLines()).anySatisfy(line -> assertThat(line).contains("2 non-BR dropped"));
    }

    @Test
    void stateIsTheUfWhetherJSearchSendsTheCodeOrTheName() throws Exception {
        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs.get(id(JAVA))).containsEntry("state", "SP");
        assertThat(jobs.get(id(DADOS))).containsEntry("state", "SP").containsEntry("city", "Campinas");
        assertThat(jobs.get(id(ESTAGIO))).containsEntry("state", "RJ");
        assertThat(jobs.get(id(ATENDENTE))).containsEntry("state", "MG")
                .containsEntry("city", "Belo Horizonte");
        assertThat(jobs.get(id(FRONT))).doesNotContainKeys("state", "city");
    }

    @Test
    void remoteOnlyWhenJobIsRemoteIsTrue() throws Exception {
        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs.get(id(JAVA))).containsEntry("remote", "remoto");
        assertThat(jobs.get(id(FRONT))).containsEntry("remote", "remoto");
        for (String jobId : List.of(DADOS, ESTAGIO, ATENDENTE, SUPORTE)) {
            assertThat(jobs.get(id(jobId))).doesNotContainKey("remote");
        }
    }

    @Test
    void contractVocabulary() throws Exception {
        assertThat(DumJSearchMapper.contract(List.of("CONTRACTOR"))).isEqualTo("pj");
        assertThat(DumJSearchMapper.contract(List.of("Contractor"))).isEqualTo("pj");
        assertThat(DumJSearchMapper.contract(List.of("INTERN"))).isEqualTo("estagio");
        assertThat(DumJSearchMapper.contract(List.of("Internship"))).isEqualTo("estagio");
        assertThat(DumJSearchMapper.contract(List.of("FULLTIME", "CONTRACTOR"))).isEqualTo("pj");
        assertThat(DumJSearchMapper.contract(List.of("Full-time and Contractor"))).isEqualTo("pj");
        assertThat(DumJSearchMapper.contract(List.of("FULLTIME"))).isNull();
        assertThat(DumJSearchMapper.contract(List.of("Part-time"))).isNull();
        assertThat(DumJSearchMapper.contract(List.of("PARTTIME"))).isNull();
        assertThat(DumJSearchMapper.contract(List.of("TEMPORARY"))).isNull();
        assertThat(DumJSearchMapper.contract(List.of("INTERN", "CONTRACTOR"))).isNull();
        assertThat(DumJSearchMapper.contract(List.of())).isNull();

        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs.get(id(DADOS))).containsEntry("contract", "pj");
        assertThat(jobs.get(id(ESTAGIO))).containsEntry("contract", "estagio");
        // job_employment_type "Contractor" only (no job_employment_types array).
        assertThat(jobs.get(id(SUPORTE))).containsEntry("contract", "pj");
        assertThat(jobs.get(id(JAVA))).doesNotContainKey("contract");
        assertThat(jobs.get(id(ATENDENTE))).doesNotContainKey("contract");
        assertThat(jobs.get(id(FRONT))).doesNotContainKey("contract");
    }

    @Test
    void salaryOnlyForMonthlyOrYearlyBrl() throws Exception {
        Map<String, Map<String, Object>> jobs = all();
        // YEAR, BRL: divided by 12, the original period kept (TRA21).
        assertThat(jobs.get(id(DADOS))).containsEntry("salaryMin", 10000.0)
                .containsEntry("salaryMax", 15000.0).containsEntry("salaryPeriod", "ano");
        // MONTH with no currency stated, minimum only.
        assertThat(jobs.get(id(SUPORTE))).containsEntry("salaryMin", 3000.0)
                .containsEntry("salaryPeriod", "mes").doesNotContainKey("salaryMax");
        // TRA21: the band is the salary enricher's (DumJSearchEnrichmentTest).
        jobs.values().forEach(job -> assertThat(job).doesNotContainKey("salaryBand"));
        // HOUR; USD; nothing stated.
        for (String jobId : List.of(ESTAGIO, ATENDENTE, FRONT)) {
            assertThat(jobs.get(id(jobId))).doesNotContainKeys("salaryMin", "salaryMax",
                    "salaryPeriod", "salaryBand");
        }

        assertThat(salary(5000, 7000, "MONTH", "brl")).containsEntry("salaryMin", 5000.0);
        assertThat(salary(null, 4000, "MONTH", null)).containsOnly(Map.entry("salaryMax", 4000.0),
                Map.entry("salaryPeriod", "mes"));
        assertThat(salary(36000, null, "YEAR", "BRL")).containsOnly(Map.entry("salaryMin", 3000.0),
                Map.entry("salaryPeriod", "ano"));
        assertThat(salary(1000, 1200, "WEEK", "BRL")).isEmpty();
        assertThat(salary(150, 200, "DAY", "BRL")).isEmpty();
        assertThat(salary(5000, 7000, null, "BRL")).isEmpty();
        assertThat(salary(5000, 7000, "MONTH", "EUR")).isEmpty();
        assertThat(salary(7000, 5000, "MONTH", "BRL")).isEmpty();
        assertThat(salary(null, null, "MONTH", "BRL")).isEmpty();
        assertThat(salary(0, 0, "MONTH", "BRL")).isEmpty();
    }

    private static Map<String, Object> salary(Number min, Number max, String period, String currency) {
        JSONObject result = new JSONObject();
        result.put("job_min_salary", min == null ? JSONObject.NULL : min);
        result.put("job_max_salary", max == null ? JSONObject.NULL : max);
        if (period != null) {
            result.put("job_salary_period", period);
        }
        if (currency != null) {
            result.put("job_salary_currency", currency);
        }
        Map<String, Object> job = new LinkedHashMap<>();
        DumJSearchMapper.putSalary(job, result);
        return job;
    }

    @Test
    void applyUrlIsJobApplyLinkVerbatim() throws Exception {
        Map<String, Map<String, Object>> jobs = all();
        assertThat(jobs.get(id(JAVA)).get("applyUrl")).isEqualTo("https://br.linkedin.com/jobs/view/"
                + "desenvolvedor-java-pleno-at-tech-brasil-4012345678?utm_campaign=google_jobs_apply"
                + "&utm_source=google_jobs_apply&utm_medium=organic");
        assertThat(jobs.get(id(DADOS)).get("applyUrl")).isEqualTo(
                "https://dadosecia.gupy.io/jobs/7123456?jobBoardSource=googleJobs&utm_source=google");
    }

    @Test
    void datesAreJSearchsNeverTheRunClock() throws Exception {
        Map<String, Object> first = all().get(id(SUPORTE));
        clock.advance(Duration.ofDays(3));
        Map<String, Object> again = all().get(id(SUPORTE));

        // No ISO date: the posting's own epoch timestamp.
        assertThat(first).containsEntry("publishedAt", "2026-10-08T12:00:00Z")
                .containsEntry("modificationDate", "2026-10-08T12:00:00Z").doesNotContainKey("expiresAt");
        assertThat(again).isEqualTo(first);
        assertThat(all().get(id(FRONT))).containsEntry("expiresAt", "2026-12-01T00:00:00.000Z");
        assertThat(all().get(id(DADOS))).doesNotContainKey("expiresAt");
    }

    // --- requests -----------------------------------------------------------------------------

    @Test
    void searchesWithTheKeyInAHeaderOnly() {
        fetch(source(null), Set.of());

        DumJobRequest first = fetcher.requests.getFirst();
        assertThat(first.method()).isEqualTo("GET");
        assertThat(first.uri().toString()).startsWith("https://jsearch.p.rapidapi.com/search?")
                .doesNotContain(API_KEY);
        assertThat(first.headers()).containsOnly(Map.entry("X-RapidAPI-Key", API_KEY),
                Map.entry("X-RapidAPI-Host", "jsearch.p.rapidapi.com"));
        assertThat(first.secrets()).containsExactly(API_KEY);
        assertThat(JSearchFixtureFetcher.params(first)).containsOnly(Map.entry("query", "vagas"),
                Map.entry("page", "1"), Map.entry("num_pages", "1"), Map.entry("country", "br"),
                Map.entry("language", "pt"), Map.entry("date_posted", "week"));
        assertThat(first.toString()).doesNotContain(API_KEY).contains("X-RapidAPI-Key=***");
    }

    @Test
    void datePostedAndApiUrlAreSettingsAndTheSourceApiUrlWins() {
        DumJobSource source = source("{\"datePosted\":\"MONTH\",\"apiUrl\":\"https://proxy.local/js/search/\","
                + "\"queries\":[\"técnico de enfermagem\"]}");
        fetch(source, Set.of());
        DumJobRequest first = fetcher.requests.getFirst();
        assertThat(first.uri().toString()).startsWith("https://proxy.local/js/search?");
        assertThat(JSearchFixtureFetcher.params(first)).containsEntry("date_posted", "month")
                .containsEntry("query", "técnico de enfermagem");

        fetcher.requests.clear();
        source.setApiUrl("https://other.local/search");
        fetch(source, Set.of());
        assertThat(fetcher.requests.getFirst().uri().toString()).startsWith("https://other.local/search?");
    }

    @Test
    void anUnknownDatePostedFailsBeforeAnyPaidCall() {
        assertThatThrownBy(() -> fetch(source("{\"datePosted\":\"yesterday\"}"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("datePosted");
        assertThatThrownBy(() -> fetch(source("{not json"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("settings");
        assertThat(fetcher.requests).isEmpty();
    }

    // --- paging -------------------------------------------------------------------------------

    @Test
    void pagesAtMostTwoByDefault() {
        List<Map<String, Object>> jobs = fetch(source(null), Set.of());
        // Page 3 would be empty, but every page costs: the default stops at 2.
        assertThat(fetcher.pages()).containsExactly("vagas:1", "vagas:2");
        assertThat(jobs).hasSize(6);
    }

    @Test
    void stopsAtAnEmptyPage() {
        fetch(source("{\"maxPages\":5}"), Set.of());
        assertThat(fetcher.pages()).containsExactly("vagas:1", "vagas:2", "vagas:3");
    }

    @Test
    void stopsAtAPageWhoseEveryPostingWasAlreadySeen() throws Exception {
        Set<String> page1 = Set.of(id(JAVA), id(DADOS), id(ESTAGIO), id(ATENDENTE));
        List<Map<String, Object>> jobs = fetch(source(null), page1);
        assertThat(fetcher.pages()).containsExactly("vagas:1");
        assertThat(jobs).hasSize(4);

        // One new posting on the page keeps paging.
        fetcher.requests.clear();
        fetch(source(null), Set.of(id(JAVA), id(DADOS), id(ESTAGIO)));
        assertThat(fetcher.pages()).containsExactly("vagas:1", "vagas:2");
    }

    // --- query rotation -----------------------------------------------------------------------

    @Test
    void theQueryRotationAdvancesOneSlotPerRun() {
        DumJobSource source = source("{\"queries\":[\"vendedor\",\"motorista\",\"atendente\"],\"maxPages\":1}");
        List<String> queried = new ArrayList<>();
        for (int run = 0; run < 4; run++) {
            fetcher.requests.clear();
            fetch(source, Set.of());
            assertThat(fetcher.requests).hasSize(1);
            queried.add(fetcher.pages().getFirst() + "->" + source.getCursor());
        }
        assertThat(queried).containsExactly("vendedor:1->1", "motorista:1->2", "atendente:1->0",
                "vendedor:1->1");
    }

    @Test
    void theDefaultQueriesAreAShortPtBrList() {
        assertThat(DumJSearchSettings.parse(null).queries()).containsExactly("vagas", "emprego",
                "estágio", "vagas home office", "vagas em São Paulo", "vagas no Rio de Janeiro");
        assertThat(DumJSearchSettings.parse("{\"queries\":[]}").queries())
                .isEqualTo(DumJSearchSettings.DEFAULT_QUERIES);
        DumJobSource source = source("{\"maxPages\":1}");
        source.setCursor("4");
        fetch(source, Set.of());
        assertThat(fetcher.pages()).containsExactly("vagas em São Paulo:1");
        assertThat(source.getCursor()).isEqualTo("5");
    }

    // --- budget -------------------------------------------------------------------------------

    @Test
    void conservativeDefaultCapsASourceCanOverride() {
        assertThat(adapter.id()).isEqualTo("jsearch");
        assertThat(adapter.defaultCaps()).containsOnly(Map.entry(Window.MINUTE, 5),
                Map.entry(Window.DAY, 30), Map.entry(Window.WEEK, 200), Map.entry(Window.MONTH, 500));

        DumJobSource source = source(null);
        source.setCapPerMonth(1000);
        DumJobCallBudget budget = budget(source, List.of());
        assertThat(budget.remaining(Window.MONTH)).hasValue(1000);
        assertThat(budget.remaining(Window.DAY)).hasValue(30);
    }

    @Test
    void aSpentMonthlyBudgetStopsTheRunCleanlyWithOneClearLine() {
        DumJobSource source = source(null);
        source.setCursor("2");
        // 500 calls 10 days ago: the day and week windows are free, the 30-day one is full.
        List<Instant> prior = Collections.nCopies(500, NOW.minus(Duration.ofDays(10)));

        DumJobFetchContext context = new DumJobFetchContext(source, budget(source, prior));

        List<Map<String, Object>> jobs = adapter.fetch(context);

        assertThat(jobs).isEmpty();
        assertThat(fetcher.requests).isEmpty();
        assertThat(source.getCursor()).isEqualTo("2");
        assertThat(context.notes()).singleElement().asString()
                .startsWith("monthly budget spent (500 calls in 30 days) before ");
        List<ILoggingEvent> monthly = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("monthly budget spent")).toList();
        assertThat(monthly).singleElement().satisfies(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).startsWith("JSearch source 'jsearch-br': monthly budget spent")
                    .contains("500-call cap");
        });
    }

    @Test
    void theMonthlyBudgetRunningOutMidRunKeepsWhatWasFetched() {
        DumJobSource source = source(null);
        List<Instant> prior = Collections.nCopies(499, NOW.minus(Duration.ofDays(10)));

        List<Map<String, Object>> jobs = adapter.fetch(new DumJobFetchContext(source, budget(source, prior)));

        assertThat(fetcher.pages()).containsExactly("vagas:1");
        assertThat(jobs).hasSize(4);
        assertThat(source.getCursor()).isEqualTo("1");
        assertThat(logLines()).filteredOn(line -> line.contains("monthly budget spent")).singleElement()
                .satisfies(line -> assertThat(line).contains("before 'vagas' page 2 with 4 posting(s)"));
    }

    @Test
    void aDailyCapIsNotReportedAsTheMonthlyBudget() {
        DumJobSource source = source(null);
        List<Instant> prior = Collections.nCopies(30, NOW.minus(Duration.ofHours(2)));

        assertThat(adapter.fetch(new DumJobFetchContext(source, budget(source, prior)))).isEmpty();
        assertThat(fetcher.requests).isEmpty();
        assertThat(logLines()).noneSatisfy(line -> assertThat(line).contains("monthly"));
        assertThat(logLines()).anySatisfy(line -> assertThat(line).contains("call budget exhausted (day cap)"));
    }

    // --- failures -----------------------------------------------------------------------------

    @Test
    void aFailedFirstCallFailsTheRunAndIsNotRetried() {
        fetcher.failing.add(1);
        DumJobSource source = source(null);
        DumJobCallBudget budget = DumJobCallBudget.unlimited(clock);
        assertThatThrownBy(() -> adapter.fetch(new DumJobFetchContext(source, budget)))
                .isInstanceOf(DumJobFetchException.class).hasMessageContaining("HTTP 500");
        assertThat(fetcher.requests).hasSize(1);
        assertThat(budget.recordedCalls()).hasSize(1);
        // The paid call was made: the next run moves on instead of paying for the same query again.
        assertThat(source.getCursor()).isEqualTo("1");
    }

    @Test
    void aLaterFailedCallEndsTheRunWithoutARetry() {
        fetcher.failing.add(2);
        DumJobSource source = source("{\"maxPages\":5}");
        DumJobCallBudget budget = DumJobCallBudget.unlimited(clock);

        List<Map<String, Object>> jobs = adapter.fetch(new DumJobFetchContext(source, budget));

        assertThat(jobs).hasSize(4);
        assertThat(fetcher.pages()).containsExactly("vagas:1", "vagas:2");
        assertThat(budget.recordedCalls()).hasSize(2);
        assertThat(logLines()).anySatisfy(line -> assertThat(line).contains("page 2 failed")
                .contains("not retried"));
    }

    @Test
    void aStatusOtherThanOkIsAFailedCall() {
        assertThatThrownBy(() -> DumJSearchAdapter.data(
                "{\"status\":\"ERROR\",\"error\":{\"message\":\"bad key k-123\",\"code\":403}}", Set.of("k-123")))
                .isInstanceOf(DumJobFetchException.class).hasMessageContaining("status ERROR")
                .hasMessageContaining("bad key ***").hasMessageNotContaining("k-123");
        assertThatThrownBy(() -> DumJSearchAdapter.data("<html>", Set.of()))
                .isInstanceOf(DumJobFetchException.class).hasMessageContaining("not a JSON object");
        assertThat(DumJSearchAdapter.data("{\"status\":\"OK\"}", Set.of())).isEmpty();
    }

    @Test
    void aMissingApiKeyFailsTheRunBeforeAnyCall() {
        DumJobSource source = source(null);
        source.setApiKey(" ");
        assertThatThrownBy(() -> fetch(source, Set.of())).isInstanceOf(DumJobFetchException.class)
                .hasMessageContaining("apiKey");
        assertThat(fetcher.requests).isEmpty();
    }

    // --- wiring -------------------------------------------------------------------------------

    @Test
    void theProviderRunsJSearchSourcesOnThisAdapter() {
        DumJobSourceRepository sources = mock(DumJobSourceRepository.class);
        DumJobSource source = source(null);
        when(sources.findByName("jsearch-br")).thenReturn(Optional.of(source));
        when(sources.findByEnabledTrue()).thenReturn(List.of(source));

        DumJobsSourceProvider provider = new DumJobsSourceProvider(sources,
                new InMemorySeenRepository().repository, new InMemoryCallRepository().repository,
                List.of(adapter), clock);

        assertThat(provider.sourceNames()).containsExactly("jsearch-br");
        assertThat(provider.resolve("jsearch-br")).hasValueSatisfying(
                s -> assertThat(s.discover()).hasSize(6));
    }
}
