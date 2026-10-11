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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobCallBudget.Window;

/** The seen-id store keeps older postings alive across quota-bound runs until they expire. */
class DumJobsStructuredSourceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String SOURCE_ID = "src-1";

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemorySeenRepository seen = new InMemorySeenRepository();
    private final InMemoryCallRepository calls = new InMemoryCallRepository();
    private final DumJobSourceRepository sourceRepository = mock(DumJobSourceRepository.class);
    private final List<DumJobFetchContext> contexts = new ArrayList<>();

    private DumJobSource jobSource() {
        return DumJobSource.builder().id(SOURCE_ID).name("adzuna-br").sourceType("adzuna")
                .locale("pt_BR").capPerMinute(5).turSNSites(Set.of("trabalharei")).build();
    }

    private DumJobsStructuredSource source(List<Map<String, Object>> fetched) {
        return source(jobSource(), context -> fetched, Map.of());
    }

    /** A source whose adapter runs {@code fetch} and declares {@code defaultCaps}. */
    private DumJobsStructuredSource source(DumJobSource jobSource,
            Function<DumJobFetchContext, List<Map<String, Object>>> fetch,
            Map<Window, Integer> defaultCaps) {
        DumJobSourceAdapter adapter = new DumJobSourceAdapter() {
            @Override
            public String id() {
                return "adzuna";
            }

            @Override
            public List<Map<String, Object>> fetch(DumJobFetchContext context) {
                contexts.add(context);
                return fetch.apply(context);
            }

            @Override
            public Map<Window, Integer> defaultCaps() {
                return defaultCaps;
            }
        };
        return new DumJobsStructuredSource(jobSource, DumJobsSchema.schema(), adapter,
                seen.repository, calls.repository, sourceRepository, clock);
    }

    /** An adapter body that asks the budget {@code n} times and fetches nothing. */
    private static Function<DumJobFetchContext, List<Map<String, Object>>> spend(int n) {
        return context -> {
            for (int i = 0; i < n; i++) {
                context.budget().tryAcquire();
            }
            return List.of();
        };
    }

    private static Map<String, Object> job(String id, Instant publishedAt) {
        Map<String, Object> job = new HashMap<>();
        job.put("id", id);
        job.put("title", "Vaga " + id);
        job.put("source", "adzuna");
        job.put("url", "https://x/" + id);
        job.put("applyUrl", "https://x/" + id + "/apply");
        job.put("publishedAt", publishedAt);
        job.put("modificationDate", publishedAt);
        return job;
    }

    @Test
    void discoverReturnsThisRunsFetchPlusUnexpiredSeenIdsAndDropsExpiredOnes() {
        seen.seed(SOURCE_ID, "old-open", NOW.plus(Duration.ofDays(3)),
                "{\"id\":\"old-open\",\"title\":\"Antiga\",\"source\":\"adzuna\"}");
        seen.seed(SOURCE_ID, "old-expired", NOW.minus(Duration.ofHours(1)), "{\"id\":\"old-expired\"}");
        seen.seed("other-source", "foreign", NOW.plus(Duration.ofDays(3)), "{\"id\":\"foreign\"}");

        List<String> ids = source(List.of(job("new-1", NOW.minus(Duration.ofDays(1))))).discover();

        assertThat(ids).containsExactly("new-1", "old-open");
        assertThat(seen.find(SOURCE_ID, "old-expired")).isEmpty();
        assertThat(seen.find("other-source", "foreign")).isPresent();
    }

    @Test
    void fetchedPostingIsRememberedWithPublishedAtPlusThirtyDays() {
        Instant published = NOW.minus(Duration.ofDays(2));
        source(List.of(job("new-1", published))).discover();

        var row = seen.find(SOURCE_ID, "new-1").orElseThrow();
        assertThat(row.getExpiresAt()).isEqualTo(published.plus(Duration.ofDays(30)));
        assertThat(row.getLastSeenAt()).isEqualTo(NOW);
        assertThat(row.getPayload()).contains("\"title\":\"Vaga new-1\"");
    }

    @Test
    void explicitExpiresAtWinsOverPublishedAt() {
        Map<String, Object> job = job("new-1", NOW.minus(Duration.ofDays(2)));
        job.put("expiresAt", "2026-10-20");
        source(List.of(job)).discover();

        assertThat(seen.find(SOURCE_ID, "new-1").orElseThrow().getExpiresAt())
                .isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
    }

    @Test
    void fetchedPostingAlreadyPastItsExpiryIsNotDiscovered() {
        seen.seed(SOURCE_ID, "stale", NOW.plus(Duration.ofDays(10)), "{\"id\":\"stale\"}");
        // Published 40 days ago and no explicit expiry: it expired 10 days ago.
        List<String> ids = source(List.of(job("stale", NOW.minus(Duration.ofDays(40))))).discover();

        assertThat(ids).isEmpty();
        assertThat(seen.find(SOURCE_ID, "stale")).isEmpty();
    }

    @Test
    void aSeenPostingStaysUntilItExpiresThenDrops() {
        source(List.of(job("a", NOW))).discover();

        // A later run whose quota only reached newer postings still reports "a".
        clock.advance(Duration.ofDays(29));
        assertThat(source(List.of(job("b", NOW.plus(Duration.ofDays(29))))).discover())
                .containsExactly("b", "a");

        clock.advance(Duration.ofDays(2));
        assertThat(source(List.of()).discover()).containsExactly("b");
    }

    @Test
    void extractRebuildsAStoredPostingWithoutCallingThePartner() {
        source(List.of(job("a", NOW))).discover();
        contexts.clear();

        DumJobsStructuredSource later = source(List.of());
        assertThat(later.discover()).containsExactly("a");
        DumStructuredRecord rec = later.extract("a").orElseThrow();

        assertThat(rec.values()).containsEntry("title", "Vaga a")
                .containsEntry("url", "https://x/a")
                .containsEntry("source", List.of("adzuna"))
                .containsEntry("publishedAt", NOW.toString());
        assertThat(rec.missingMandatoryFields()).isEmpty();
    }

    @Test
    void extractWithoutDiscoverReadsTheStoreOnly() {
        source(List.of(job("a", NOW))).discover();
        contexts.clear();

        DumJobsStructuredSource byId = source(List.of(job("z", NOW)));
        assertThat(byId.extract("a")).isPresent();
        assertThat(byId.extract("missing")).isEmpty();
        assertThat(contexts).isEmpty();
    }

    @Test
    void malformedValueDropsTheFieldNotThePosting() {
        Map<String, Object> job = job("a", NOW);
        // Two values for a single-valued field.
        job.put("company", List.of("Tech Brasil", "Outra"));
        job.put("text", "Descrição");
        DumJobsStructuredSource source = source(List.of(job));
        source.discover();

        DumStructuredRecord rec = source.extract("a").orElseThrow();
        assertThat(rec.has("company")).isFalse();
        assertThat(rec.has("text")).isTrue();
    }

    @Test
    void extractLeavesTheEnrichedFieldsToTheirEnrichers() {
        Map<String, Object> job = job("a", NOW);
        job.put("contract", "clt");
        job.put("seniority", "pleno");
        job.put("salaryMin", 5000.5);
        job.put("salaryMax", 7000);
        job.put("salaryPeriod", "mes");
        job.put("salaryBand", "5k-8k");
        job.put("salaryRaw", "R$ 5.000");
        DumJobsStructuredSource source = source(List.of(job));
        source.discover();

        // TRA21: salary, contract and seniority are the enrichers' to write, read from the posting.
        DumStructuredRecord rec = source.extract("a").orElseThrow();
        assertThat(rec.values()).doesNotContainKeys("contract", "seniority", "salaryMin", "salaryMax",
                "salaryPeriod", "salaryBand", "salaryRaw");
        assertThat(source.posting("a")).containsEntry("contract", "clt")
                .containsEntry("salaryRaw", "R$ 5.000");
    }

    @Test
    void postingWithoutIdIsSkipped() {
        Map<String, Object> noId = job("x", NOW);
        noId.remove("id");
        assertThat(source(List.of(noId, job("a", NOW))).discover()).containsExactly("a");
    }

    @Test
    void adapterGetsTheSourceAndItsCappedBudget() {
        source(List.of()).discover();

        assertThat(contexts).hasSize(1);
        assertThat(contexts.getFirst().source().getName()).isEqualTo("adzuna-br");
        assertThat(contexts.getFirst().budget().remaining(DumJobCallBudget.Window.MINUTE)).hasValue(5);
    }

    @Test
    void sourceMetadata() {
        DumJobsStructuredSource source = source(List.of());
        assertThat(source.getLocale()).isEqualTo(Locale.of("pt", "BR"));
        assertThat(source.getSiteNames()).containsExactly("trabalharei");
        assertThat(source.getProviderName()).isEqualTo("JOBS");
    }

    @Test
    void grantedCallsArePersistedAndSeedTheNextRunsBudget() {
        DumJobSource capped = jobSource().toBuilder().capPerMinute(null).capPerDay(3).build();

        source(capped, spend(2), Map.of()).discover();
        assertThat(calls.calls(SOURCE_ID)).containsExactly(NOW, NOW);

        // A second run an hour later starts with the 2 calls of the first: 1 left today.
        clock.advance(Duration.ofHours(1));
        source(capped, spend(5), Map.of()).discover();
        assertThat(calls.calls(SOURCE_ID)).hasSize(3);
        assertThat(contexts.getLast().budget().remaining(Window.DAY)).hasValue(0);

        // The next day the window has rolled over.
        clock.advance(Duration.ofDays(1));
        source(capped, spend(1), Map.of()).discover();
        assertThat(contexts.getLast().budget().remaining(Window.DAY)).hasValue(2);
    }

    @Test
    void callsOlderThanTheRetentionArePrunedAndOtherSourcesIgnored() {
        calls.seed(SOURCE_ID, NOW.minus(Duration.ofDays(32)));
        calls.seed(SOURCE_ID, NOW.minus(Duration.ofDays(10)));
        calls.seed("other-source", NOW.minus(Duration.ofHours(1)));

        source(jobSource().toBuilder().capPerMonth(5).build(), spend(0), Map.of()).discover();

        assertThat(calls.calls(SOURCE_ID)).containsExactly(NOW.minus(Duration.ofDays(10)));
        assertThat(calls.calls("other-source")).hasSize(1);
        assertThat(contexts.getLast().budget().remaining(Window.MONTH)).hasValue(4);
    }

    @Test
    void theAdaptersDefaultCapsApplyWhereTheSourceSetsNone() {
        DumJobSource source = jobSource().toBuilder().capPerMinute(null).capPerDay(0).build();
        source(source, spend(0), Map.of(Window.MINUTE, 25, Window.DAY, 250, Window.WEEK, 1000))
                .discover();

        DumJobCallBudget budget = contexts.getLast().budget();
        assertThat(budget.remaining(Window.MINUTE)).hasValue(25);
        // An explicit 0 on the source means uncapped, even with an adapter default.
        assertThat(budget.remaining(Window.DAY)).isEmpty();
        assertThat(budget.remaining(Window.WEEK)).hasValue(1000);
        assertThat(budget.remaining(Window.MONTH)).isEmpty();
    }

    @Test
    void theAdapterSeesOnlyIdsOfEarlierRuns() {
        seen.seed(SOURCE_ID, "old", NOW.plus(Duration.ofDays(3)), "{\"id\":\"old\"}");
        List<Boolean> answers = new ArrayList<>();
        source(jobSource(), context -> {
            answers.add(context.wasSeen("old"));
            answers.add(context.wasSeen("new-1"));
            return List.of(job("new-1", NOW));
        }, Map.of()).discover();

        assertThat(answers).containsExactly(true, false);
    }

    @Test
    void aCursorTheAdapterMovesIsSavedOnTheStoredRow() {
        DumJobSource stored = jobSource();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(stored));

        source(jobSource(), context -> {
            context.source().setCursor("3");
            return List.of();
        }, Map.of()).discover();

        assertThat(stored.getCursor()).isEqualTo("3");
        verify(sourceRepository).save(stored);
    }

    @Test
    void anUnchangedCursorIsNotSavedAndAFailingFetchStillSavesAMovedOne() {
        source(List.of()).discover();
        verify(sourceRepository, never()).save(any());

        DumJobSource stored = jobSource();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(stored));
        DumJobsStructuredSource failing = source(jobSource(), context -> {
            context.source().setCursor("1");
            throw new IllegalStateException("partner down");
        }, Map.of());

        assertThatThrownBy(failing::discover).hasMessage("partner down");
        assertThat(stored.getCursor()).isEqualTo("1");
    }
    @Test
    void theAdapterSeesWhenEachIdWasFirstFetchedAndTheStoreKeepsIt() {
        Instant earlier = NOW.minus(Duration.ofDays(4));
        seen.seed(SOURCE_ID, "old", NOW.plus(Duration.ofDays(3)), "{\"id\":\"old\"}");
        seen.find(SOURCE_ID, "old").orElseThrow().setFirstSeenAt(earlier);
        List<Instant> answers = new ArrayList<>();
        source(jobSource(), context -> {
            answers.add(context.firstSeenAt("old"));
            answers.add(context.firstSeenAt("new-1"));
            return List.of(job("old", NOW), job("new-1", NOW));
        }, Map.of()).discover();

        // An earlier run's time for a known id, this run's for a new one.
        assertThat(answers).containsExactly(earlier, NOW);
        assertThat(seen.find(SOURCE_ID, "new-1").orElseThrow().getFirstSeenAt()).isEqualTo(NOW);

        clock.advance(Duration.ofDays(1));
        source(List.of(job("new-1", NOW))).discover();
        var row = seen.find(SOURCE_ID, "new-1").orElseThrow();
        assertThat(row.getFirstSeenAt()).isEqualTo(NOW);
        assertThat(row.getLastSeenAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(seen.find(SOURCE_ID, "old").orElseThrow().getFirstSeenAt()).isEqualTo(earlier);
    }

    @Test
    void aRowFromBeforeFirstSeenAtExistedFallsBackToItsLastSeenAt() {
        // seed() leaves firstSeenAt null and lastSeenAt one day before the expiry.
        seen.seed(SOURCE_ID, "legacy", NOW.plus(Duration.ofDays(3)), "{\"id\":\"legacy\"}");
        Instant legacyLastSeen = NOW.plus(Duration.ofDays(2));
        List<Instant> answers = new ArrayList<>();
        source(jobSource(), context -> {
            answers.add(context.firstSeenAt("legacy"));
            return List.of(job("legacy", NOW));
        }, Map.of()).discover();

        assertThat(answers).containsExactly(legacyLastSeen);
        assertThat(seen.find(SOURCE_ID, "legacy").orElseThrow().getFirstSeenAt()).isEqualTo(legacyLastSeen);
    }
}
