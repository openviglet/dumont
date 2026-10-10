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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;

/** The seen-id store keeps older postings alive across quota-bound runs until they expire. */
class DumJobsStructuredSourceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String SOURCE_ID = "src-1";

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemorySeenRepository seen = new InMemorySeenRepository();
    private final List<DumJobFetchContext> contexts = new ArrayList<>();

    private DumJobSource jobSource() {
        return DumJobSource.builder().id(SOURCE_ID).name("adzuna-br").sourceType("adzuna")
                .locale("pt_BR").capPerMinute(5).turSNSites(Set.of("trabalharei")).build();
    }

    private DumJobsStructuredSource source(List<Map<String, Object>> fetched) {
        DumJobSourceAdapter adapter = new DumJobSourceAdapter() {
            @Override
            public String id() {
                return "adzuna";
            }

            @Override
            public List<Map<String, Object>> fetch(DumJobFetchContext context) {
                contexts.add(context);
                return fetched;
            }
        };
        return new DumJobsStructuredSource(jobSource(), DumJobsSchema.schema(), adapter,
                seen.repository, clock);
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
        job.put("salaryMin", "a combinar");
        job.put("salaryMax", 5000.5);
        DumJobsStructuredSource source = source(List.of(job));
        source.discover();

        DumStructuredRecord rec = source.extract("a").orElseThrow();
        assertThat(rec.has("salaryMin")).isFalse();
        assertThat(rec.has("salaryMax")).isTrue();
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
}
