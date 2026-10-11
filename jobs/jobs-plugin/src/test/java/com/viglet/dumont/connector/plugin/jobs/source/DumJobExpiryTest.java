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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.viglet.dumont.connector.plugin.jobs.DumJobsPlugin;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSeen;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.adzuna.DumAdzunaAdapter;
import com.viglet.dumont.connector.plugin.jobs.source.bne.DumBneAdapter;
import com.viglet.dumont.connector.plugin.jobs.source.dedupe.DumJobDeduplicator;
import com.viglet.dumont.connector.plugin.jobs.source.expiry.DumJobLinkChecker;
import com.viglet.dumont.connector.plugin.jobs.source.expiry.DumJobRateLimiter;
import com.viglet.dumont.connector.plugin.jobs.source.expiry.DumJobTombstones;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetchException;
import com.viglet.dumont.connector.plugin.jobs.source.jooble.DumJoobleAdapter;
import com.viglet.dumont.connector.plugin.jobs.source.jsearch.DumJSearchAdapter;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.occupation.DumJobOccupationEnricher;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Job expiry end to end (trabalharei TRA24): the dated/undated rule, the dead-link check and the
 * tombstones, through the jobs plugin into a context that tracks and sweeps the way Dumont does.
 */
class DumJobExpiryTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String TOKEN = "tok-9d2e44a0c1";
    private static final String TEXT = "Entregas na região metropolitana.";

    /** Apply links answer 200 unless scripted; every HEAD is recorded. */
    static final class Links implements DumJobLinkChecker.Transport {
        final Map<String, Integer> status = new HashMap<>();
        final Set<String> broken = new java.util.HashSet<>();
        final List<String> heads = new ArrayList<>();

        @Override
        public DumJobLinkChecker.Response head(URI uri) throws IOException {
            heads.add(uri.toString());
            if (broken.contains(uri.toString())) {
                throw new IOException("connection reset");
            }
            return new DumJobLinkChecker.Response(status.getOrDefault(uri.toString(), 200), null);
        }
    }

    /** A test adapter declaring the two TRA24 properties. */
    static DumJobSourceAdapter adapter(String type, boolean dated, boolean checkLinks,
            Map<String, List<Map<String, Object>>> feeds) {
        return new DumJobSourceAdapter() {
            @Override
            public String id() {
                return type;
            }

            @Override
            public List<Map<String, Object>> fetch(DumJobFetchContext context) {
                return feeds.getOrDefault(type, List.of()).stream()
                        .map(posting -> (Map<String, Object>) new LinkedHashMap<>(posting)).toList();
            }

            @Override
            public boolean datedPostings() {
                return dated;
            }

            @Override
            public boolean checkApplyLinks() {
                return checkLinks;
            }
        };
    }

    /** Sources adzuna (dated), bne (undated) and jooble (no link check), one store, one site. */
    final class World {
        final MutableClock clock = new MutableClock(NOW);
        final InMemorySeenRepository seen = new InMemorySeenRepository();
        final InMemoryCallRepository calls = new InMemoryCallRepository();
        final InMemoryFingerprintRepository fingerprints = new InMemoryFingerprintRepository();
        final InMemoryTombstoneRepository pending = new InMemoryTombstoneRepository();
        final FakeSite site = new FakeSite();
        final Links links = new Links();
        final Map<String, List<Map<String, Object>>> feeds = new HashMap<>();
        final Map<String, String> settings = new HashMap<>();
        final DumJobDedupeTest.SweepingContext context = new DumJobDedupeTest.SweepingContext();
        final DumJobsPlugin plugin;

        World(boolean dedupe) {
            DumJobSourceRepository sources = mock(DumJobSourceRepository.class);
            when(sources.findByName(anyString())).thenAnswer(inv -> {
                String type = inv.getArgument(0, String.class).replace("-br", "");
                return Optional.of(DumJobSource.builder().id("id-" + type).name(type + "-br").sourceType(type)
                        .capPerDay(100).settings(settings.get(type)).turSNSites(Set.of("trabalharei")).build());
            });
            List<DumJobSourceAdapter> adapters = List.of(adapter("adzuna", true, true, feeds),
                    adapter("bne", false, true, feeds), adapter("jooble", true, false, feeds));
            DumJobLinkChecker checker = new DumJobLinkChecker(20, new DumJobRateLimiter(0), links);
            DumJobTombstones tombstones = new DumJobTombstones("https://trabalharei.example", TOKEN,
                    pending.repository, site, DumJobLocationEnricher.standard(),
                    DumJobOccupationEnricher.dictionaryOnly(), clock);
            DumJobsSourceProvider provider = new DumJobsSourceProvider(sources, seen.repository, calls.repository,
                    adapters, clock, dedupe ? new DumJobDeduplicator(fingerprints.repository) : null, checker,
                    tombstones);
            plugin = new DumJobsPlugin(context, provider);
        }

        World feed(String type, Map<String, Object>... postings) {
            feeds.put(type, new ArrayList<>(List.of(postings)));
            return this;
        }

        /** Run {@code type}'s source; answers the document ids it published. */
        List<String> run(String type) {
            context.lastRun.clear();
            plugin.indexAll(type + "-br");
            return List.copyOf(context.lastRun);
        }

        void advance(Duration by) {
            clock.advance(by);
        }

        DumJobSeen row(String type, String id) {
            return seen.find("id-" + type, id).orElseThrow();
        }
    }

    static Map<String, Object> posting(String id, String source, String title, Instant publishedAt) {
        Map<String, Object> posting = new LinkedHashMap<>();
        posting.put("id", id);
        posting.put("title", title);
        posting.put("text", TEXT);
        posting.put("company", "Transportes Rota Ltda");
        posting.put("city", "Campinas");
        posting.put("state", "SP");
        posting.put("source", source);
        posting.put("url", DumJobNormalizer.siteUrl(null, title, id));
        posting.put("applyUrl", "https://apply.example/" + id);
        posting.put("publishedAt", publishedAt.toString());
        posting.put("modificationDate", publishedAt.toString());
        return posting;
    }

    static Map<String, Object> with(Map<String, Object> posting, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(posting);
        copy.put(key, value);
        return copy;
    }

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureEveryLog() {
        logs = new ListAppender<>();
        logs.start();
        root().addAppender(logs);
    }

    @AfterEach
    void release() {
        root().detachAppender(logs);
    }

    private static Logger root() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private List<String> lines(String containing) {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(l -> l.contains(containing))
                .toList();
    }

    // --- the expiry rule ------------------------------------------------------------------------

    @Test
    void theAdaptersDeclareWhetherTheyAreDatedAndWhetherTheirLinksMayBeChecked() {
        assertThat(new DumBneAdapter(null).datedPostings()).isFalse();
        assertThat(new DumBneAdapter(null).checkApplyLinks()).isTrue();
        assertThat(new DumJoobleAdapter(null).checkApplyLinks()).isFalse();
        assertThat(new DumJoobleAdapter(null).datedPostings()).isTrue();
        assertThat(new DumAdzunaAdapter(null).datedPostings()).isTrue();
        assertThat(new DumAdzunaAdapter(null).checkApplyLinks()).isTrue();
        assertThat(new DumJSearchAdapter(null).datedPostings()).isTrue();
        assertThat(new DumJSearchAdapter(null).checkApplyLinks()).isTrue();
    }

    @Test
    void aDatedPostingExpiresThirtyDaysAfterItsPublishedAtEvenIfStillReturned() {
        World world = new World(false).feed("adzuna", posting("adzuna-1", "adzuna", "Motorista", NOW));
        assertThat(world.run("adzuna")).containsExactly("adzuna-1");
        assertThat(world.row("adzuna", "adzuna-1").getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));

        world.advance(Duration.ofDays(29));
        assertThat(world.run("adzuna")).containsExactly("adzuna-1");
        // Re-fetched: lastSeenAt moves, the expiry does not.
        assertThat(world.row("adzuna", "adzuna-1").getLastSeenAt()).isEqualTo(NOW.plus(Duration.ofDays(29)));
        assertThat(world.row("adzuna", "adzuna-1").getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));

        world.advance(Duration.ofDays(2));
        assertThat(world.run("adzuna")).isEmpty();
        assertThat(world.context.index).isEmpty();
    }

    @Test
    void anUndatedPostingLivesWhileTheSourceKeepsReturningItThenThirtyDaysMore() {
        // BNE's publishedAt is the first fetch; it must not cap the posting's life.
        World world = new World(false).feed("bne", posting("bne-1", "bne", "Motorista", NOW));
        assertThat(world.run("bne")).containsExactly("bne-1");

        for (int run = 1; run <= 3; run++) {
            world.advance(Duration.ofDays(25));
            assertThat(world.run("bne")).containsExactly("bne-1");
            Instant now = world.clock.instant();
            assertThat(world.row("bne", "bne-1").getLastSeenAt()).isEqualTo(now);
            assertThat(world.row("bne", "bne-1").getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
        }
        Instant lastSeen = world.clock.instant();

        // BNE stops listing it: kept until 30 days after the last fetch, then gone.
        world.feed("bne");
        world.advance(Duration.ofDays(29));
        assertThat(world.run("bne")).containsExactly("bne-1");
        world.advance(Duration.ofDays(1));
        assertThat(world.clock.instant()).isEqualTo(lastSeen.plus(Duration.ofDays(30)));
        assertThat(world.run("bne")).isEmpty();
        assertThat(world.seen.rows).isEmpty();
    }

    @Test
    void anUndatedRowStoredUnderTheOldRuleIsReDatedFromItsLastFetch() {
        World world = new World(false);
        // Stored before TRA24: expiry = first fetch + 30 days, an hour from now; last fetched 2 days ago.
        world.seen.seed("id-bne", "bne-9", NOW.plus(Duration.ofHours(1)),
                new org.json.JSONObject(posting("bne-9", "bne", "Motorista", NOW.minus(Duration.ofDays(29))))
                        .toString());
        DumJobSeen row = world.row("bne", "bne-9");
        row.setLastSeenAt(NOW.minus(Duration.ofDays(2)));

        world.advance(Duration.ofHours(2));
        assertThat(world.run("bne")).containsExactly("bne-9");
        assertThat(world.row("bne", "bne-9").getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(28)));
    }

    @Test
    void theExplicitExpiresAtWinsForAnUndatedSourceToo() {
        World world = new World(false).feed("bne",
                with(posting("bne-1", "bne", "Motorista", NOW), "expiresAt", "2026-10-15T00:00:00Z"));
        world.run("bne");
        assertThat(world.row("bne", "bne-1").getExpiresAt()).isEqualTo(Instant.parse("2026-10-15T00:00:00Z"));
    }

    // --- dead apply links -----------------------------------------------------------------------

    @Test
    void eachRunChecksASampleLeastRecentlyCheckedFirst() {
        World world = new World(false);
        world.settings.put("adzuna", "{\"linkCheck\":{\"sampleSize\":2}}");
        world.feed("adzuna", posting("adzuna-a", "adzuna", "A", NOW), posting("adzuna-b", "adzuna", "B", NOW),
                posting("adzuna-c", "adzuna", "C", NOW), posting("adzuna-d", "adzuna", "D", NOW),
                posting("adzuna-e", "adzuna", "E", NOW));

        world.run("adzuna");
        assertThat(world.links.heads).containsExactly("https://apply.example/adzuna-a",
                "https://apply.example/adzuna-b");
        world.links.heads.clear();
        world.advance(Duration.ofHours(1));
        world.run("adzuna");
        assertThat(world.links.heads).containsExactly("https://apply.example/adzuna-c",
                "https://apply.example/adzuna-d");
        world.links.heads.clear();
        world.advance(Duration.ofHours(1));
        world.run("adzuna");
        assertThat(world.links.heads).containsExactly("https://apply.example/adzuna-e",
                "https://apply.example/adzuna-a");
    }

    @Test
    void theDefaultSampleIsTwentyAndStoredPostingsAreSampledToo() {
        World world = new World(false);
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            many.add(posting("adzuna-%02d".formatted(i), "adzuna", "Vaga " + i, NOW));
        }
        world.feeds.put("adzuna", many);
        world.run("adzuna");
        assertThat(world.links.heads).hasSize(20);

        // Nothing fetched now: the five never checked come from the store.
        world.links.heads.clear();
        world.feed("adzuna");
        world.advance(Duration.ofHours(1));
        world.run("adzuna");
        assertThat(world.links.heads).hasSize(20).startsWith("https://apply.example/adzuna-20",
                "https://apply.example/adzuna-21", "https://apply.example/adzuna-22",
                "https://apply.example/adzuna-23", "https://apply.example/adzuna-24");
    }

    @Test
    void notFoundOrGoneExpiresThePostingNowAndARefetchDoesNotReviveIt() {
        World world = new World(false).feed("adzuna", posting("adzuna-1", "adzuna", "Motorista", NOW),
                posting("adzuna-2", "adzuna", "Ajudante", NOW), posting("adzuna-3", "adzuna", "Conferente", NOW));
        assertThat(world.run("adzuna")).containsExactly("adzuna-1", "adzuna-2", "adzuna-3");

        world.links.status.put("https://apply.example/adzuna-1", 404);
        world.links.status.put("https://apply.example/adzuna-2", 410);
        world.advance(Duration.ofDays(1));
        assertThat(world.run("adzuna")).containsExactly("adzuna-3");
        assertThat(world.context.index).containsOnlyKeys("adzuna-3");
        Instant death = world.clock.instant();
        assertThat(world.row("adzuna", "adzuna-1").getLinkDeadAt()).isEqualTo(death);
        assertThat(world.row("adzuna", "adzuna-1").getExpiresAt()).isEqualTo(death);

        // The partner keeps returning both: they stay expired, and are not checked again.
        world.links.heads.clear();
        world.advance(Duration.ofDays(1));
        assertThat(world.run("adzuna")).containsExactly("adzuna-3");
        assertThat(world.links.heads).containsExactly("https://apply.example/adzuna-3");
        assertThat(world.row("adzuna", "adzuna-1").getLastSeenAt()).isEqualTo(world.clock.instant());

        // Kept as a marker until 30 days after the partner last returned it, then pruned.
        Instant lastSeen = world.clock.instant();
        world.feed("adzuna", posting("adzuna-3", "adzuna", "Conferente", NOW));
        world.advance(Duration.ofDays(29));
        world.run("adzuna");
        assertThat(world.seen.find("id-adzuna", "adzuna-1")).isPresent();
        world.advance(Duration.ofDays(1));
        assertThat(world.clock.instant()).isEqualTo(lastSeen.plus(Duration.ofDays(30)));
        world.run("adzuna");
        assertThat(world.seen.find("id-adzuna", "adzuna-1")).isEmpty();
    }

    @Test
    void aServerErrorOrANetworkFailureIsNotDeadAndIsCheckedAgainFirst() {
        World world = new World(false);
        world.settings.put("adzuna", "{\"linkCheck\":{\"sampleSize\":2}}");
        world.feed("adzuna", posting("adzuna-a", "adzuna", "A", NOW), posting("adzuna-b", "adzuna", "B", NOW),
                posting("adzuna-c", "adzuna", "C", NOW));
        world.links.status.put("https://apply.example/adzuna-a", 503);
        world.links.broken.add("https://apply.example/adzuna-b");

        assertThat(world.run("adzuna")).containsExactly("adzuna-a", "adzuna-b", "adzuna-c");
        assertThat(world.row("adzuna", "adzuna-a").getLinkCheckedAt()).isNull();
        assertThat(world.row("adzuna", "adzuna-b").getLinkCheckedAt()).isNull();

        // Retried on the next run, ahead of the posting never checked.
        world.links.heads.clear();
        world.links.status.clear();
        world.links.broken.clear();
        world.advance(Duration.ofHours(1));
        world.run("adzuna");
        assertThat(world.links.heads).containsExactly("https://apply.example/adzuna-a",
                "https://apply.example/adzuna-b");
        assertThat(world.row("adzuna", "adzuna-a").getLinkCheckedAt()).isEqualTo(world.clock.instant());
    }

    @Test
    void joobleLinksAreNeverCheckedAndChecksNeverSpendThePartnerBudget() {
        World world = new World(false).feed("jooble", posting("jooble-1", "jooble", "Motorista", NOW))
                .feed("adzuna", posting("adzuna-1", "adzuna", "Motorista", NOW),
                        posting("adzuna-2", "adzuna", "Ajudante", NOW));
        world.links.status.put("https://apply.example/jooble-1", 404);

        assertThat(world.run("jooble")).containsExactly("jooble-1");
        assertThat(world.links.heads).isEmpty();
        assertThat(lines("Job source 'jooble-br' run:")).singleElement().asString()
                .contains("link check off for jooble links");

        world.run("adzuna");
        assertThat(world.links.heads).hasSize(2);
        // The test adapters make no partner call, so no call was recorded for the link checks either.
        assertThat(world.calls.calls("id-adzuna")).isEmpty();
    }

    // --- tombstones -----------------------------------------------------------------------------

    @Test
    void aVanishedPostingGetsOneTombstoneWithTheExactJson() {
        World world = new World(false).feed("adzuna",
                with(posting("adzuna-1", "adzuna", "Motorista Carreteiro", NOW), "expiresAt", "2026-10-12T00:00:00Z"),
                posting("adzuna-2", "adzuna", "Ajudante", NOW));
        world.run("adzuna");
        assertThat(world.site.requests).isEmpty();

        world.advance(Duration.ofDays(3));
        assertThat(world.run("adzuna")).containsExactly("adzuna-2");
        assertThat(world.site.bodies()).containsExactly("{\"id\":\"adzuna-1\",\"title\":\"Motorista Carreteiro\","
                + "\"city\":\"Campinas\",\"state\":\"SP\","
                + "\"occupation\":\"motoristas-de-veiculos-de-cargas-em-geral\","
                + "\"expiredAt\":\"2026-10-12T00:00:00Z\"}");
        assertThat(world.site.requests.getFirst().uri())
                .hasToString("https://trabalharei.example/api/internal/tombstones/");

        world.advance(Duration.ofDays(1));
        world.run("adzuna");
        assertThat(world.site.requests).hasSize(1);
    }

    @Test
    void aDeadLinkSendsATombstoneDatedNowButAPostingNeverPublishedSendsNone() {
        World world = new World(false).feed("adzuna", posting("adzuna-1", "adzuna", "Motorista", NOW));
        world.run("adzuna");

        world.advance(Duration.ofDays(1));
        world.links.status.put("https://apply.example/adzuna-1", 404);
        // adzuna-2 is new and dead on arrival: never published, so no tombstone.
        world.feed("adzuna", posting("adzuna-1", "adzuna", "Motorista", NOW),
                posting("adzuna-2", "adzuna", "Ajudante", NOW));
        world.links.status.put("https://apply.example/adzuna-2", 410);
        assertThat(world.run("adzuna")).isEmpty();

        assertThat(world.site.bodies()).singleElement().asString().startsWith("{\"id\":\"adzuna-1\"")
                .endsWith("\"expiredAt\":\"2026-10-11T12:00:00Z\"}");
    }

    @Test
    void aGroupStaysAliveWhileAnyMemberIsOpenAndItsTombstoneGoesOnceWhenTheLastExpires() {
        Map<String, Object> adzuna = with(posting("adzuna-1", "adzuna", "Motorista Carreteiro", NOW),
                "expiresAt", "2026-10-15T00:00:00Z");
        Map<String, Object> bne = posting("bne-1", "bne", "Motorista Carreteiro", NOW);
        World world = new World(true).feed("adzuna", adzuna).feed("bne", bne);
        List<String> group = world.run("adzuna");
        assertThat(group).singleElement().asString().startsWith("job-");
        assertThat(world.run("bne")).isEqualTo(group);

        // Adzuna's member expires; BNE keeps listing its own: the group lives, no tombstone.
        world.advance(Duration.ofDays(10));
        assertThat(world.run("adzuna")).isEqualTo(group);
        assertThat(world.run("bne")).isEqualTo(group);
        assertThat(world.context.index).containsKey(group.getFirst());
        assertThat(world.site.requests).isEmpty();
        assertThat(world.pending.rows).isEmpty();

        // BNE stops listing it; 30 days after its last fetch the group's last member is gone.
        world.feed("bne");
        world.advance(Duration.ofDays(31));
        assertThat(world.run("bne")).isEmpty();
        assertThat(world.run("adzuna")).isEmpty();
        assertThat(world.context.index).doesNotContainKey(group.getFirst());
        assertThat(world.site.bodies()).singleElement().asString()
                .startsWith("{\"id\":\"" + group.getFirst() + "\",\"title\":\"Motorista Carreteiro\"")
                .contains("\"expiredAt\":\"2026-11-19T12:00:00Z\"");

        world.advance(Duration.ofDays(1));
        world.run("adzuna");
        world.run("bne");
        assertThat(world.site.requests).hasSize(1);
    }

    @Test
    void aMemberThatLosesOrDiesByLinkSendsNothingWhileItsGroupLives() {
        Map<String, Object> adzuna = with(posting("adzuna-1", "adzuna", "Motorista Carreteiro", NOW),
                "salaryMin", 4000);
        World world = new World(true).feed("adzuna", adzuna)
                .feed("jooble", posting("jooble-1", "jooble", "Motorista Carreteiro", NOW));
        String group = world.run("adzuna").getFirst();
        world.run("jooble");
        assertThat(world.context.applyUrl(group)).isEqualTo("https://apply.example/adzuna-1");

        // The winner's apply link dies: the group moves to Jooble's posting and stays published.
        world.links.status.put("https://apply.example/adzuna-1", 404);
        world.advance(Duration.ofDays(1));
        assertThat(world.run("adzuna")).containsExactly(group);
        assertThat(world.context.applyUrl(group)).isEqualTo("https://apply.example/jooble-1");
        assertThat(world.run("jooble")).containsExactly(group);
        assertThat(world.site.requests).isEmpty();
        assertThat(world.pending.rows).isEmpty();
    }

    @Test
    void aFailedTombstoneIsRetriedOnTheNextRunAndNeverBlocksIndexing() {
        World world = new World(false).feed("adzuna",
                with(posting("adzuna-1", "adzuna", "Motorista", NOW), "expiresAt", "2026-10-11T00:00:00Z"),
                posting("adzuna-2", "adzuna", "Ajudante", NOW));
        world.run("adzuna");
        world.site.failure = new DumJobFetchException("HTTP 502 from POST https://trabalharei.example/api/internal/tombstones/");

        world.advance(Duration.ofDays(2));
        assertThat(world.run("adzuna")).containsExactly("adzuna-2");
        assertThat(world.context.index).containsOnlyKeys("adzuna-2");
        assertThat(world.pending.find("adzuna-1")).get().extracting(row -> row.getAttempts()).isEqualTo(1);

        world.site.failure = null;
        world.advance(Duration.ofDays(1));
        world.run("adzuna");
        assertThat(world.site.requests).hasSize(2);
        assertThat(world.pending.rows).isEmpty();
        world.run("adzuna");
        assertThat(world.site.requests).hasSize(2);
    }

    @Test
    void aDocumentPublishedAgainBeforeItsTombstoneWentOutCancelsIt() {
        World world = new World(false).feed("adzuna",
                with(posting("adzuna-1", "adzuna", "Motorista", NOW), "expiresAt", "2026-10-11T00:00:00Z"));
        world.run("adzuna");
        world.site.failure = new DumJobFetchException("HTTP 503");
        world.advance(Duration.ofDays(2));
        world.run("adzuna");
        assertThat(world.pending.find("adzuna-1")).isPresent();

        // The partner extends the posting: it is published again, and its tombstone must not go out.
        world.site.failure = null;
        world.feed("adzuna",
                with(posting("adzuna-1", "adzuna", "Motorista", NOW), "expiresAt", "2026-10-25T00:00:00Z"));
        world.advance(Duration.ofDays(1));
        assertThat(world.run("adzuna")).containsExactly("adzuna-1");
        assertThat(world.pending.rows).isEmpty();
        assertThat(world.site.requests).hasSize(1);
    }

    @Test
    void theTokenIsNeverLoggedOverAWholeRun() {
        World world = new World(false).feed("adzuna",
                with(posting("adzuna-1", "adzuna", "Motorista", NOW), "expiresAt", "2026-10-11T00:00:00Z"));
        world.run("adzuna");
        world.site.failure = new IllegalStateException("proxy said: Authorization: Bearer " + TOKEN);
        world.advance(Duration.ofDays(2));
        world.run("adzuna");

        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).isNotEmpty()
                .noneMatch(line -> line.contains(TOKEN));
        assertThat(world.pending.find("adzuna-1").orElseThrow().getLastError()).doesNotContain(TOKEN);
    }

    // --- the run summary ------------------------------------------------------------------------

    @Test
    void eachRunLogsOneSummaryLinePerSource() {
        World world = new World(false).feed("adzuna",
                with(posting("adzuna-1", "adzuna", "Motorista", NOW), "expiresAt", "2026-10-11T00:00:00Z"),
                posting("adzuna-2", "adzuna", "Ajudante", NOW), posting("adzuna-3", "adzuna", "Conferente", NOW));
        world.run("adzuna");
        assertThat(lines("Job source 'adzuna-br' run:")).containsExactly("Job source 'adzuna-br' run: 3 active, "
                + "3 new, 0 expired (0 by dead link); 3 fetched, 0 kept from earlier runs; 3 link(s) checked, "
                + "0 dead, 0 to retry; 0 tombstone(s) queued");

        logs.list.clear();
        world.advance(Duration.ofDays(2));
        world.links.status.put("https://apply.example/adzuna-3", 404);
        world.links.status.put("https://apply.example/adzuna-4", 500);
        world.feed("adzuna", posting("adzuna-4", "adzuna", "Estoquista", NOW));
        world.run("adzuna");
        assertThat(lines("Job source 'adzuna-br' run:")).containsExactly("Job source 'adzuna-br' run: 2 active, "
                + "1 new, 2 expired (1 by dead link); 1 fetched, 2 kept from earlier runs; 3 link(s) checked, "
                + "1 dead, 1 to retry; 2 tombstone(s) queued");
        assertThat(lines("tombstone(s) sent")).containsExactly(
                "Job source 'adzuna-br': 2 tombstone(s) sent, 0 kept for the next run");

        logs.list.clear();
        world.run("bne");
        assertThat(lines("Job source 'bne-br' run:")).containsExactly("Job source 'bne-br' run: 0 active, "
                + "0 new, 0 expired (0 by dead link); 0 fetched, 0 kept from earlier runs; 0 link(s) checked, "
                + "0 dead, 0 to retry; 0 tombstone(s) queued");
    }

    @Test
    void withoutAnExpiryCollaboratorTheRunSaysTheLinkCheckIsOff() {
        InMemorySeenRepository seen = new InMemorySeenRepository();
        DumJobsStructuredSource source = new DumJobsStructuredSource(
                DumJobSource.builder().id("s").name("plain").sourceType("adzuna").build(), DumJobsSchema.schema(),
                adapter("adzuna", true, true, Map.of("adzuna", List.of(posting("adzuna-1", "adzuna", "M", NOW)))),
                seen.repository, new InMemoryCallRepository().repository, mock(DumJobSourceRepository.class),
                new MutableClock(NOW));
        assertThat(source.discover()).containsExactly("adzuna-1");
        assertThat(lines("Job source 'plain' run:")).singleElement().asString().contains("; link check off; ");
    }
}
