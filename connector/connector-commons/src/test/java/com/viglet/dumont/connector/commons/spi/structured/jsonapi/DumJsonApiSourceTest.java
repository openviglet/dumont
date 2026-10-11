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

package com.viglet.dumont.connector.commons.spi.structured.jsonapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourcePlugin;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSourceProvider;

/** Walking a paged JSON API from its manifest, and when the walk counts as complete (roadmap D92). */
class DumJsonApiSourceTest {

    private static final String FIELDS = """
            "id": "id",
            "fields": [
              {"name": "title", "type": "TEXT", "mandatory": true},
              {"name": "company", "path": "company.name"},
              {"name": "salary", "type": "INT"},
              {"name": "skills", "path": "skills.name", "multiValued": true}
            ]
            """;

    private final List<DumJsonApiRequest> requests = new ArrayList<>();

    @Test
    void pagesUntilAShortPage() {
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs?page={page}&n={size}"},
                 "paging": {"mode": "page", "size": 2}, "records": "results", %s}
                """.formatted(FIELDS), pages(page(1, 2), page(3, 4), page(5)));

        assertEquals(List.of("1", "2", "3", "4", "5"), source.discover());
        assertTrue(source.discoveryComplete());
        assertEquals(List.of("https://api.example/jobs?page=1&n=2", "https://api.example/jobs?page=2&n=2",
                "https://api.example/jobs?page=3&n=2"), urls());
    }

    @Test
    void stopsAtTheDeclaredTotal() {
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs/{page}"},
                 "paging": {"mode": "page", "totalPath": "count"}, "records": "results", %s}
                """.formatted(FIELDS), pages(page(1, 2).put("count", 4), page(3, 4).put("count", 4),
                page(5, 6)));

        assertEquals(List.of("1", "2", "3", "4"), source.discover());
        assertTrue(source.discoveryComplete());
        assertEquals(2, source.calls());
    }

    @Test
    void followsACursorUntilItRunsOut() {
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs?after={cursor}"},
                 "paging": {"mode": "cursor", "cursorPath": "next"}, "records": "results", %s}
                """.formatted(FIELDS), pages(page(1).put("next", "a b"), page(2).put("next", "c"),
                page(3)));

        assertEquals(List.of("1", "2", "3"), source.discover());
        assertTrue(source.discoveryComplete());
        assertEquals(List.of("https://api.example/jobs?after=", "https://api.example/jobs?after=a+b",
                "https://api.example/jobs?after=c"), urls());
    }

    @Test
    void aPageOfIdsAlreadySeenEndsTheWalk() {
        // An API that ignores the page parameter would otherwise be walked until the cap.
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs?p={page}"},
                 "paging": {"mode": "page"}, "records": "results", %s}
                """.formatted(FIELDS), request -> page(1, 2).toString());

        assertEquals(List.of("1", "2"), source.discover());
        assertTrue(source.discoveryComplete());
        assertEquals(2, source.calls());
    }

    @Test
    void postsAnOffsetBodyWithNumbersStillNumbers() {
        DumJsonApiSource source = source("""
                {"name": "bne", "request": {"method": "POST", "url": "https://bne.example/vagas",
                   "headers": {"Authorization": "Bearer {secret:token}"},
                   "body": {"offset": "{offset}", "limit": "{size}", "q": "dev {offset}"}},
                 "paging": {"mode": "offset", "size": 2}, "records": "data.listVagas", %s}
                """.formatted(FIELDS), pages(
                new JSONObject().put("data", new JSONObject().put("listVagas", page(1, 2).get("results"))),
                new JSONObject().put("data", new JSONObject().put("listVagas", new JSONArray()))),
                Map.of("token", "t0k"));

        assertEquals(List.of("1", "2"), source.discover());
        DumJsonApiRequest second = requests.get(1);
        assertEquals("POST", second.method());
        assertEquals("Bearer t0k", second.headers().get("Authorization"));
        JSONObject body = new JSONObject(second.body());
        assertEquals(2, body.get("offset"));
        assertEquals(2, body.get("limit"));
        assertEquals("dev 2", body.get("q"));
    }

    @Test
    void theCallCapLeavesTheWalkIncomplete() {
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs?p={page}"},
                 "paging": {"mode": "page"}, "budget": {"maxCallsPerRun": 2}, "records": "results", %s}
                """.formatted(FIELDS), request -> page(requests.size() * 10).toString());

        assertEquals(List.of("10", "20"), source.discover());
        assertFalse(source.discoveryComplete(), "a budget-bound run did not see the whole source");
        assertEquals(2, source.calls());
        assertEquals(List.of("stopped at the call cap of 2 per run"), source.runNotes());
    }

    @Test
    void thePageCapLeavesTheWalkIncomplete() {
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs?p={page}"},
                 "paging": {"mode": "page", "maxPages": 3}, "records": "results", %s}
                """.formatted(FIELDS), request -> page(requests.size()).toString());

        assertEquals(3, source.discover().size());
        assertFalse(source.discoveryComplete());
    }

    @Test
    void aFailedPageKeepsWhatCameBeforeAndLeavesTheWalkIncomplete() {
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs?p={page}"},
                 "paging": {"mode": "page"}, "records": "results", %s}
                """.formatted(FIELDS), request -> {
                    if (requests.size() == 2) {
                        throw new IOException("HTTP 503");
                    }
                    return page(requests.size()).toString();
                });

        assertEquals(List.of("1"), source.discover());
        assertFalse(source.discoveryComplete());
        assertEquals(List.of("stopped: call 2 failed (HTTP 503)"), source.runNotes());
    }

    @Test
    void aBareArrayResponseIsOnePage() {
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs"}, %s}
                """.formatted(FIELDS), request -> page(1, 2).getJSONArray("results").toString());

        assertEquals(List.of("1", "2"), source.discover());
        assertTrue(source.discoveryComplete());
    }

    @Test
    void mapsFieldsAndDropsWhatDoesNotFit() {
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs"}, "records": "results", %s}
                """.formatted(FIELDS), request -> new JSONObject().put("results", new JSONArray()
                .put(new JSONObject("""
                        {"id": 9, "title": "Engineer", "company": {"name": "Acme"}, "salary": "a lot",
                         "skills": [{"name": "java"}, {"name": "sql"}]}
                        """))).toString());

        source.discover();
        DumStructuredRecord rec = source.extract("9").orElseThrow();
        assertEquals("Engineer", rec.values().get("title"));
        assertEquals("Acme", rec.values().get("company"));
        assertEquals(List.of("java", "sql"), rec.values().get("skills"));
        assertNull(rec.values().get("salary"), "a non-numeric value is dropped, never coerced");
        assertEquals("json", rec.provenance().get("company").strategy());
        assertEquals("company.name", rec.provenance().get("company").selector());
        assertTrue(source.extract("missing").isEmpty());
    }

    @Test
    void refusesToBindWithoutTheSecretsTheManifestUses() {
        DumJsonApiManifest manifest = DumJsonApiManifest.parse("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs?key={secret:key}"}, %s}
                """.formatted(FIELDS));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DumJsonApiSource.builder(manifest, request -> "[]").sourceName("jobs").build());
        assertTrue(e.getMessage().contains("[key]"));
    }

    @Test
    void secretsAreEncodedIntoTheUrl() {
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs?key={secret:key}"}, %s}
                """.formatted(FIELDS), request -> "[]", Map.of("key", "a&b"));

        source.discover();
        assertEquals("https://api.example/jobs?key=a%26b", requests.getFirst().url());
    }

    @Test
    void thePacerSpacesCalls() {
        long[] now = {1_000};
        List<Long> sleeps = new ArrayList<>();
        DumJsonApiSource.Pacer pacer = DumJsonApiSource.Pacer.sleeping(() -> now[0], ms -> {
            sleeps.add(ms);
            now[0] += ms;
        });
        DumJsonApiManifest manifest = DumJsonApiManifest.parse("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs?p={page}"},
                 "paging": {"mode": "page", "maxPages": 3}, "budget": {"callsPerMinute": 30},
                 "records": "results", %s}
                """.formatted(FIELDS));
        DumJsonApiSource source = DumJsonApiSource.builder(manifest, request -> {
            requests.add(request);
            now[0] += 500;
            return page(requests.size()).toString();
        }).sourceName("jobs").pacer(pacer).build();

        source.discover();
        assertEquals(List.of(1_500L, 1_500L), sleeps, "30 calls a minute is one every 2s");
    }

    @Test
    void anIncompleteWalkFinishesTheRunStandalone() {
        // The plugin half of D92: a budget-bound run must not deindex what it never fetched.
        CapturingContext context = new CapturingContext();
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs?p={page}"},
                 "paging": {"mode": "page"}, "budget": {"maxCallsPerRun": 1}, "records": "results", %s}
                """.formatted(FIELDS), request -> page(1, 2).toString());

        new JsonApiPlugin(context, source).indexAll("jobs");

        assertEquals(2, context.items.size(), "what was fetched is still published");
        assertTrue(context.standalone, "an incomplete walk finishes standalone");
        // D96: the run record says the run was partial, and why.
        assertEquals(Boolean.TRUE, context.recordedPartial);
        assertEquals(List.of("discovery stopped before the end of the source",
                "stopped at the call cap of 1 per run"), context.recordedNotes);
    }

    @Test
    void aCompleteWalkFinishesTheRunWithTheSweep() {
        CapturingContext context = new CapturingContext();
        DumJsonApiSource source = source("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs"}, "records": "results", %s}
                """.formatted(FIELDS), request -> page(1, 2).toString());

        new JsonApiPlugin(context, source).indexAll("jobs");

        assertEquals(2, context.items.size());
        assertFalse(context.standalone, "a complete walk lets the sweep remove what disappeared");
        assertEquals(null, context.recordedPartial, "a complete run with nothing to say records nothing");
    }

    @Test
    void aDailyAllowanceIsSharedAcrossRunsAndRenewsAtMidnight() {
        DumInMemoryJsonApiState state = new DumInMemoryJsonApiState();
        MutableClock clock = new MutableClock(Instant.parse("2026-10-10T22:00:00Z"));
        String manifest = """
                {"name": "jobs", "request": {"url": "https://api.example/jobs?p={page}"},
                 "paging": {"mode": "page"}, "budget": {"callsPerDay": 3}, "records": "results", %s}
                """.formatted(FIELDS);
        DumJsonFetcher endless = request -> page(requests.size()).toString();

        DumJsonApiSource first = bound(manifest, endless, state, clock);
        assertEquals(3, first.discover().size());
        assertFalse(first.discoveryComplete(), "the day's allowance stopped the walk");

        DumJsonApiSource sameDay = bound(manifest, endless, state, clock);
        assertEquals(List.of(), sameDay.discover(), "nothing is left today");
        assertEquals(0, sameDay.calls());

        clock.now = Instant.parse("2026-10-11T00:00:01Z");
        DumJsonApiSource nextDay = bound(manifest, endless, state, clock);
        assertEquals(3, nextDay.discover().size(), "a new UTC day renews the allowance");
    }

    @Test
    void windowsStartAtMidnightMondayAndTheFirst() {
        Instant saturday = Instant.parse("2026-10-10T15:30:00Z");
        assertEquals(Instant.parse("2026-10-10T00:00:00Z"), DumJsonApiState.Window.DAY.start(saturday));
        assertEquals(Instant.parse("2026-10-05T00:00:00Z"), DumJsonApiState.Window.WEEK.start(saturday));
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), DumJsonApiState.Window.MONTH.start(saturday));
    }

    @Test
    void anIncrementalSourceDeindexesOnlyWhatExpired() {
        DumInMemoryJsonApiState state = new DumInMemoryJsonApiState();
        MutableClock clock = new MutableClock(Instant.parse("2026-10-01T08:00:00Z"));
        String manifest = """
                {"name": "jobs", "request": {"url": "https://api.example/jobs"},
                 "incremental": {"expiry": "P7D"}, "records": "results", %s}
                """.formatted(FIELDS);

        CapturingContext day1 = new CapturingContext();
        new JsonApiPlugin(day1, bound(manifest, request -> page(1, 2).toString(), state, clock))
                .indexAll("jobs");
        assertTrue(day1.standalone, "an incremental run never sweeps");
        assertEquals(List.of(), day1.deindexed);

        // Two days later only job 2 is returned: job 1 is missing but has not expired.
        clock.now = Instant.parse("2026-10-03T08:00:00Z");
        CapturingContext day3 = new CapturingContext();
        new JsonApiPlugin(day3, bound(manifest, request -> page(2).toString(), state, clock))
                .indexAll("jobs");
        assertEquals(List.of(), day3.deindexed);

        // Nine days after it was last seen, job 1 expires; job 2, seen today, stays.
        clock.now = Instant.parse("2026-10-10T08:00:00Z");
        CapturingContext day10 = new CapturingContext();
        new JsonApiPlugin(day10, bound(manifest, request -> page(2).toString(), state, clock))
                .indexAll("jobs");
        assertEquals(List.of("1"), day10.deindexed);
        assertEquals(List.of("site"), day10.deindexSites);
        assertTrue(day10.standalone);
        assertEquals(Boolean.FALSE, day10.recordedPartial, "a complete incremental walk is not partial");
        assertEquals(List.of("1 expired record(s) deindexed"), day10.recordedNotes);
    }

    @Test
    void aWalkThatFoundNothingExpiresNothing() {
        DumInMemoryJsonApiState state = new DumInMemoryJsonApiState();
        state.markSeen(List.of("old"), Instant.parse("2026-01-01T00:00:00Z"));
        MutableClock clock = new MutableClock(Instant.parse("2026-10-10T08:00:00Z"));
        DumJsonApiSource down = bound("""
                {"name": "jobs", "request": {"url": "https://api.example/jobs"},
                 "incremental": {"expiry": "P7D"}, "records": "results", %s}
                """.formatted(FIELDS), request -> {
                    throw new IOException("HTTP 503");
                }, state, clock);

        assertEquals(List.of(), down.expiredIds(), "an outage says nothing about what is gone");
    }

    // --- helpers -------------------------------------------------------------------------------

    private DumJsonApiSource bound(String manifest, DumJsonFetcher responses, DumJsonApiState state,
            Clock clock) {
        return DumJsonApiSource.builder(DumJsonApiManifest.parse(manifest), request -> {
            requests.add(request);
            return responses.fetch(request);
        }).sourceName("jobs").siteNames(List.of("site")).state(state).clock(clock)
                .pacer(minIntervalMs -> { }).build();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private DumJsonApiSource source(String manifest, DumJsonFetcher responses) {
        return source(manifest, responses, Map.of());
    }

    private DumJsonApiSource source(String manifest, DumJsonFetcher responses,
            Map<String, String> secrets) {
        return DumJsonApiSource.builder(DumJsonApiManifest.parse(manifest), request -> {
            requests.add(request);
            return responses.fetch(request);
        }).sourceName("jobs").siteNames(List.of("site")).secrets(secrets)
                .pacer(minIntervalMs -> { }).build();
    }

    /** Answers the n-th call with the n-th page; past the last, an empty page. */
    private DumJsonFetcher pages(JSONObject... pages) {
        return request -> {
            int index = requests.size() - 1;
            return index < pages.length ? pages[index].toString()
                    : new JSONObject().put("results", new JSONArray()).toString();
        };
    }

    private static JSONObject page(int... ids) {
        JSONArray results = new JSONArray();
        for (int id : ids) {
            results.put(new JSONObject().put("id", id).put("title", "Job " + id));
        }
        return new JSONObject().put("results", results);
    }

    private List<String> urls() {
        return requests.stream().map(DumJsonApiRequest::url).toList();
    }

    private static final class JsonApiPlugin extends DumStructuredSourcePlugin {
        private final DumStructuredSource source;

        JsonApiPlugin(DumConnectorContext context, DumStructuredSource source) {
            super(context);
            this.source = source;
        }

        @Override
        protected DumStructuredSourceProvider provider() {
            return new DumStructuredSourceProvider() {
                @Override
                public String getProviderName() {
                    return source.getProviderName();
                }

                @Override
                public Collection<String> sourceNames() {
                    return List.of(source.getSourceName());
                }

                @Override
                public Optional<DumStructuredSource> resolve(String sourceName) {
                    return Optional.of(source).filter(s -> s.getSourceName().equals(sourceName));
                }
            };
        }
    }

    private static final class CapturingContext implements DumConnectorContext {
        private final List<DumJobItemWithSession> items = new ArrayList<>();
        private final List<String> deindexed = new ArrayList<>();
        private List<String> deindexSites = List.of();
        private boolean standalone;
        private Boolean recordedPartial;
        private List<String> recordedNotes = List.of();

        @Override
        public void recordRunOutcome(DumConnectorSession session, boolean partial, List<String> notes) {
            recordedPartial = partial;
            recordedNotes = List.copyOf(notes);
        }

        @Override
        public int deIndexById(DumConnectorSession session, Collection<String> objectIds,
                List<String> sites) {
            deindexed.addAll(objectIds);
            deindexSites = sites;
            return objectIds.size();
        }

        @Override
        public boolean addJobItem(DumJobItemWithSession dumJobItemWithSession) {
            return items.add(dumJobItemWithSession);
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            this.standalone = standalone;
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
}
