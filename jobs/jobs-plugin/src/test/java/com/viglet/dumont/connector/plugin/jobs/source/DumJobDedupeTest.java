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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.plugin.jobs.DumJobsPlugin;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobFingerprintEntry;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.dedupe.DumJobDeduplicator;
import com.viglet.dumont.connector.plugin.jobs.source.dedupe.DumJobFingerprint;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryEnricher;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Cross-source deduplication end to end (trabalharei TRA23): four sources, one per adapter, run one at
 * a time through the jobs plugin into a context that tracks and sweeps the way Dumont does.
 */
class DumJobDedupeTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String TEXT = "Desenvolvimento de APIs REST em Java 21 e Spring Boot.";

    /** The same Acme opening as each partner's adapter writes it, plus one unrelated posting. */
    private static Map<String, Object> adzuna() {
        return posting("adzuna-4123456789", "adzuna", "Desenvolvedor Java Pleno", TEXT,
                "Acme Tecnologia Ltda", "https://www.adzuna.com.br/land/ad/4123456789",
                "2026-10-06T10:00:00Z", Map.of("city", "São Paulo", "state", "SP",
                        "locationRaw", "São Paulo, São Paulo"));
    }

    private static Map<String, Object> jooble() {
        return posting("jooble-7712", "jooble", "DESENVOLVEDOR JAVA", TEXT, "ACME TECNOLOGIA S/A",
                "https://br.jooble.org/desc/7712", "2026-10-08T00:00:00", Map.of("locationRaw", "São Paulo, SP"));
    }

    private static Map<String, Object> jsearch() {
        return posting("jsearch-ab12cd34ef56ab12", "jsearch", "Desenvolvedor Java Sênior", TEXT,
                "Acme Tecnologia", "https://acme.gupy.io/jobs/1", "2026-10-07T15:30:00.000Z",
                Map.of("city", "Sao Paulo", "state", "SP"));
    }

    /** BNE never names the employer, so its copy of the opening is a group of its own. */
    private static Map<String, Object> bne() {
        return posting("bne-998877", "bne", "Desenvolvedor Java", TEXT, null,
                "https://www.bne.com.br/vaga-de-emprego/998877", "2026-10-09",
                Map.of("city", "São Paulo", "state", "SP"));
    }

    private static Map<String, Object> adzunaOther() {
        return posting("adzuna-555", "adzuna", "Analista Financeiro", TEXT, "Beta Finanças",
                "https://www.adzuna.com.br/land/ad/555", "2026-10-06T10:00:00Z",
                Map.of("city", "Campinas", "state", "SP"));
    }

    private static Map<String, Object> posting(String id, String source, String title, String text,
            String company, String applyUrl, String publishedAt, Map<String, Object> place) {
        Map<String, Object> posting = new LinkedHashMap<>();
        posting.put("id", id);
        posting.put("title", title);
        posting.put("text", text);
        if (company != null) {
            posting.put("company", company);
        }
        posting.putAll(place);
        posting.put("source", source);
        posting.put("url", DumJobNormalizer.siteUrl(null, title, id));
        posting.put("applyUrl", applyUrl);
        posting.put("publishedAt", publishedAt);
        posting.put("modificationDate", publishedAt);
        return posting;
    }

    private static Map<String, Object> with(Map<String, Object> posting, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(posting);
        copy.put(key, value);
        return copy;
    }

    /**
     * A context that keeps Dumont's bookkeeping: a tracking row per {@code (objectId, source)} with the
     * checksum and transaction that last touched it; an unchanged checksum is not resent; a finished
     * full run deletes from the index every id its source tracked but did not touch, whoever else
     * tracks it.
     */
    static final class SweepingContext implements DumConnectorContext {
        record Row(String checksum, String transactionId) {
        }

        final Map<String, Map<String, Row>> tracking = new HashMap<>();
        final Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        final List<String> lastRun = new ArrayList<>();

        @Override
        public boolean addJobItem(DumJobItemWithSession item) {
            TurSNJobItem job = item.turSNJobItem();
            Map<String, Row> rows = tracking.computeIfAbsent(item.session().getSource(), s -> new HashMap<>());
            Row old = rows.get(job.getId());
            String checksum = new TreeMap<>(job.getAttributes()).toString();
            if (old == null || !old.checksum().equals(checksum)) {
                index.put(job.getId(), job.getAttributes());
            }
            rows.put(job.getId(), new Row(checksum, item.session().getTransactionId()));
            lastRun.add(job.getId());
            return true;
        }

        @Override
        public void finishIndexing(DumConnectorSession session, boolean standalone) {
            if (standalone) {
                return;
            }
            Map<String, Row> rows = tracking.getOrDefault(session.getSource(), new HashMap<>());
            rows.entrySet().removeIf(row -> {
                boolean swept = !row.getValue().transactionId().equals(session.getTransactionId());
                if (swept) {
                    index.remove(row.getKey());
                }
                return swept;
            });
        }

        @Override
        public List<DumConnectorIndexing> getIndexingItem(String objectId, String source, String provider) {
            return List.of();
        }

        @Override
        public List<String> getObjectIdByDependency(String source, String provider, List<String> ids) {
            return List.of();
        }

        Object applyUrl(String id) {
            Object value = index.get(id).get(DumJobsSchema.APPLY_URL);
            return value instanceof List<?> list ? list.getFirst() : value;
        }
    }

    /** Four job sources (one per adapter) sharing one seen store and one fingerprint index. */
    static final class World {
        final MutableClock clock = new MutableClock(NOW);
        final InMemoryFingerprintRepository fingerprints = new InMemoryFingerprintRepository();
        final DumJobDeduplicator deduplicator = new DumJobDeduplicator(fingerprints.repository);
        final Map<String, List<Map<String, Object>>> feeds = new HashMap<>();
        final SweepingContext context = new SweepingContext();
        final DumJobsPlugin plugin;

        World() {
            DumJobSourceRepository sources = mock(DumJobSourceRepository.class);
            when(sources.findByName(anyString())).thenAnswer(inv -> {
                String type = inv.getArgument(0, String.class).replace("-br", "");
                return Optional.of(DumJobSource.builder().id("id-" + type).name(type + "-br").sourceType(type)
                        .turSNSites(Set.of("trabalharei")).build());
            });
            List<DumJobSourceAdapter> adapters = new ArrayList<>();
            for (String type : List.of("adzuna", "jooble", "bne", "jsearch")) {
                feeds.put(type, new ArrayList<>());
                adapters.add(new DumJobSourceAdapter() {
                    @Override
                    public String id() {
                        return type;
                    }

                    @Override
                    public List<Map<String, Object>> fetch(DumJobFetchContext fetch) {
                        return List.copyOf(feeds.get(type));
                    }
                });
            }
            DumJobsSourceProvider provider = new DumJobsSourceProvider(sources,
                    new InMemorySeenRepository().repository, new InMemoryCallRepository().repository, adapters,
                    clock, deduplicator);
            plugin = new DumJobsPlugin(context, provider);
        }

        World feed(String type, Map<String, Object> posting) {
            feeds.get(type).add(posting);
            return this;
        }

        /** Run {@code type}'s source; answers the ids it published. */
        List<String> run(String type) {
            context.lastRun.clear();
            plugin.indexAll(type + "-br");
            return List.copyOf(context.lastRun);
        }
    }

    private static String groupOf(Map<String, Object> posting) {
        return new DumJobDeduplicator(new InMemoryFingerprintRepository().repository).fingerprint(posting)
                .orElseThrow().groupId();
    }

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLogs() {
        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(DumJobDeduplicator.class)).addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        ((Logger) LoggerFactory.getLogger(DumJobDeduplicator.class)).detachAppender(appender);
    }

    @Test
    void theSameOpeningFromThreeAdaptersIsOneDocumentAndAnAnonymousCopyStaysApart() {
        World world = new World().feed("adzuna", adzuna()).feed("adzuna", adzunaOther())
                .feed("jooble", jooble()).feed("jsearch", jsearch()).feed("bne", bne());
        String acme = groupOf(adzuna());

        assertThat(world.run("adzuna")).containsExactly(acme, groupOf(adzunaOther()));
        assertThat(world.run("jooble")).containsExactly(acme);
        assertThat(world.run("jsearch")).containsExactly(acme);
        assertThat(world.run("bne")).containsExactly(groupOf(bne())).doesNotContain(acme);

        assertThat(groupOf(jooble())).isEqualTo(acme);
        assertThat(groupOf(jsearch())).isEqualTo(acme);
        assertThat(world.context.index).containsOnlyKeys(acme, groupOf(adzunaOther()), groupOf(bne()));
        // No partner id reaches the index, and the kept document's site URL names the group id.
        assertThat(world.context.index.get(acme).get(DumJobsSchema.URL).toString()).endsWith("-" + acme + "/");
        // Every member's apply link is kept in the index table for a later task.
        assertThat(world.fingerprints.group(DumJobFingerprint.hashOf(acme)))
                .extracting(DumJobFingerprintEntry::getApplyUrl)
                .containsExactlyInAnyOrder("https://www.adzuna.com.br/land/ad/4123456789",
                        "https://br.jooble.org/desc/7712", "https://acme.gupy.io/jobs/1");
    }

    @Test
    void theKeptRecordIsTheRichestThenThePayPerClickPartnerThenTheFixedOrder() {
        String acme = groupOf(adzuna());

        World equal = new World().feed("adzuna", adzuna()).feed("jsearch", jsearch()).feed("jooble", jooble());
        equal.run("adzuna");
        equal.run("jsearch");
        assertThat(equal.context.applyUrl(acme)).isEqualTo("https://www.adzuna.com.br/land/ad/4123456789");
        equal.run("jooble");
        assertThat(equal.context.applyUrl(acme)).isEqualTo("https://br.jooble.org/desc/7712");

        World longer = new World().feed("adzuna", adzuna()).feed("jooble", jooble())
                .feed("jsearch", with(jsearch(), "text", TEXT + " Benefícios: VR, VA e plano de saúde."));
        longer.run("jooble");
        longer.run("adzuna");
        longer.run("jsearch");
        assertThat(longer.context.applyUrl(acme)).isEqualTo("https://acme.gupy.io/jobs/1");

        Map<String, Object> paid = with(adzuna(), "salaryMin", 6000);
        assertThat(DumJobSalaryEnricher.hasSalary(paid)).isTrue();
        World salary = new World().feed("adzuna", paid).feed("jooble", jooble())
                .feed("jsearch", with(jsearch(), "text", TEXT + " Benefícios: VR, VA e plano de saúde."));
        salary.run("jsearch");
        salary.run("jooble");
        salary.run("adzuna");
        assertThat(salary.context.applyUrl(acme)).isEqualTo("https://www.adzuna.com.br/land/ad/4123456789");
        assertThat(salary.context.index.get(acme)).containsKey(DumJobsSchema.SALARY_MIN);
    }

    @Test
    void theKeptIdIsTheSameInEveryRunWhicheverSourceArrivesFirst() {
        World forward = new World().feed("adzuna", adzuna()).feed("jooble", jooble()).feed("jsearch", jsearch());
        World backward = new World().feed("adzuna", adzuna()).feed("jooble", jooble()).feed("jsearch", jsearch());
        List<String> first = forward.run("adzuna");
        forward.run("jooble");
        forward.run("jsearch");
        backward.run("jsearch");
        backward.run("jooble");
        List<String> last = backward.run("adzuna");

        assertThat(first).isEqualTo(last).containsExactly(groupOf(adzuna()));
        assertThat(forward.context.index.keySet()).isEqualTo(backward.context.index.keySet());
        assertThat(forward.context.applyUrl(first.getFirst()))
                .isEqualTo(backward.context.applyUrl(first.getFirst()))
                .isEqualTo("https://br.jooble.org/desc/7712");

        // A second round of runs publishes the same id again, from every source.
        assertThat(forward.run("adzuna")).isEqualTo(first);
        assertThat(forward.run("jooble")).isEqualTo(first);
        assertThat(forward.run("jsearch")).isEqualTo(first);
    }

    @Test
    void aLaterSourcesRunNeverDeindexesTheGroupDocumentAnotherSourceWon() {
        String acme = groupOf(adzuna());
        World world = new World()
                .feed("adzuna", with(adzuna(), "expiresAt", "2026-10-20T00:00:00Z"))
                .feed("jooble", with(jooble(), "salaryRaw", "R$ 6.000 a R$ 8.000 por mês"));

        world.run("adzuna");
        assertThat(world.context.applyUrl(acme)).isEqualTo("https://www.adzuna.com.br/land/ad/4123456789");
        world.run("jooble");
        assertThat(world.context.applyUrl(acme)).isEqualTo("https://br.jooble.org/desc/7712");

        // Adzuna's next full run still publishes the group, now with Jooble's posting: its sweep keeps it.
        assertThat(world.run("adzuna")).containsExactly(acme);
        assertThat(world.context.index).containsKey(acme);
        assertThat(world.context.applyUrl(acme)).isEqualTo("https://br.jooble.org/desc/7712");

        // Adzuna's own posting expires; it still holds the group while Jooble's is open.
        world.clock.advance(Duration.ofDays(15));
        appender.list.clear();
        assertThat(world.run("adzuna")).containsExactly(acme);
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(line -> line.contains("0 open posting(s) published as 1 document(s)")
                        && line.contains("1 group(s) kept for other sources"));
        assertThat(world.context.index).containsKey(acme);
        assertThat(world.run("jooble")).containsExactly(acme);
        assertThat(world.context.index).containsKey(acme);
        assertThat(world.context.tracking.get("adzuna-br")).containsKey(acme);

        // Once every member has expired, no source publishes it and the next sweep removes it.
        world.clock.advance(Duration.ofDays(20));
        assertThat(world.run("jooble")).isEmpty();
        assertThat(world.context.index).doesNotContainKey(acme);
        assertThat(world.fingerprints.rows).isEmpty();
        assertThat(world.run("adzuna")).isEmpty();
        assertThat(world.context.tracking.get("adzuna-br")).doesNotContainKey(acme);
    }

    @Test
    void eachRunLogsItsDuplicateRatePerSourcePair() {
        World world = new World().feed("adzuna", adzuna()).feed("adzuna", adzunaOther())
                .feed("jooble", jooble()).feed("jsearch", jsearch()).feed("bne", bne());
        world.run("adzuna");
        world.run("jooble");
        appender.list.clear();

        world.run("jsearch");
        world.run("adzuna");

        List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.contains("duplicates")).toList();
        assertThat(lines).containsExactly(
                "Job source 'jsearch-br' duplicates: 1 open posting(s) published as 1 document(s); shared with "
                        + "adzuna-br 1 (100.0%), jooble-br 1 (100.0%); 0 within the source; 0 without a "
                        + "fingerprint; 0 group(s) kept for other sources",
                "Job source 'adzuna-br' duplicates: 2 open posting(s) published as 2 document(s); shared with "
                        + "jooble-br 1 (50.0%), jsearch-br 1 (50.0%); 0 within the source; 0 without a "
                        + "fingerprint; 0 group(s) kept for other sources");
        // The counts were logged and cleared.
        assertThat(world.deduplicator.summary("adzuna-br")).isEqualTo(DumJobDeduplicator.Summary.EMPTY);
    }

    @Test
    void twoPostingsOfOneSourceAreOneDocumentToo() {
        World world = new World().feed("adzuna", adzuna())
                .feed("adzuna", with(with(adzuna(), "id", "adzuna-4123456790"), "title", "Desenvolvedor Java"));
        assertThat(world.run("adzuna")).containsExactly(groupOf(adzuna()));
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(line -> line.contains("1 within the source"));
    }

    @Test
    void indexByIdOnAPostingIdPublishesItsGroupDocument() {
        World world = new World().feed("adzuna", adzuna()).feed("jooble", jooble());
        world.run("adzuna");
        world.run("jooble");
        world.context.lastRun.clear();

        world.plugin.indexById("adzuna-br", List.of("adzuna-4123456789"));

        assertThat(world.context.lastRun).containsExactly(groupOf(adzuna()));
    }
}
