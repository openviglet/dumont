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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.json.JSONObject;

import com.viglet.dumont.connector.commons.spi.structured.DumStructuredRecord;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSchema;
import com.viglet.dumont.connector.commons.spi.structured.DumStructuredSource;
import com.viglet.dumont.connector.plugin.jobs.DumJobsPlugin;
import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobCall;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSeen;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobCallRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSeenRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobSourceRepository;
import com.viglet.dumont.connector.plugin.jobs.source.dedupe.DumJobDeduplicator;
import com.viglet.dumont.connector.plugin.jobs.source.dedupe.DumJobFingerprint;
import com.viglet.dumont.connector.plugin.jobs.source.expiry.DumJobLinkChecker;
import com.viglet.dumont.connector.plugin.jobs.source.expiry.DumJobTombstones;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link DumStructuredSource} over one partner job API (trabalharei <strong>TRA13</strong>).
 *
 * <p>
 * Quota-bound partner APIs only return the newest postings, but Dumont's run-end sweep deindexes
 * every id a full run does not publish. So {@link #discover()} does not stop at this run's fetch: it
 * records every fetched posting in the seen-id store ({@link DumJobSeen}) and reports the union of
 * this run's fetch and every stored posting that has not expired. {@link #extract(String)} rebuilds a
 * stored posting from the payload kept with it, so the sweep sees it again without a partner call
 * (an unchanged posting is not re-sent to Turing — the indexing chain skips it by checksum).
 *
 * <p>
 * <strong>Expiry (trabalharei TRA24).</strong> A posting expires at its own {@code expiresAt} when the
 * partner gives one. Otherwise a dated partner's posting expires 30 days after its {@code publishedAt};
 * an undated partner's ({@link DumJobSourceAdapter#datedPostings()} {@code false}: BNE, whose
 * {@code publishedAt} is only the first fetch) 30 days after the run that last fetched it, so a
 * posting the partner keeps listing keeps living. Every fetch refreshes {@code lastSeenAt}. Expired
 * rows are pruned on each discover, so the next full run deindexes them.
 *
 * <p>
 * <strong>Dead apply links.</strong> With a {@link DumJobLinkChecker}, each run HEAD-checks the apply
 * links of a sample of the source's open postings, least recently checked first, unless the adapter
 * opts out ({@link DumJobSourceAdapter#checkApplyLinks()}, Jooble). A 404/410 expires the posting at
 * once: its row is kept, marked {@code linkDeadAt}, so the partner returning it again does not revive
 * it, and pruned 30 days after the partner last returned it. A network error or a 5xx changes
 * nothing, so the posting is checked again next run. The checks never touch the call budget.
 *
 * <p>
 * <strong>Tombstones.</strong> With {@link DumJobTombstones}, a document that leaves the index in this
 * run is queued for the job site: a posting published on its own id when it expires (by date, by dead
 * link, or by a partner date that moved into the past), a group document when the deduplicator
 * reports its last member gone. A member of a live group expiring sends nothing. A document published
 * again has its pending tombstone cancelled. The plugin sends the queue at the end of the run.
 *
 * <p>
 * <strong>Run summary.</strong> Each discover logs one line per source: active, new and expired
 * postings, how many expired by a dead link, and the link check's counts (the run record cannot carry
 * them: D96).
 *
 * <p>
 * Each run's call budget is seeded with the calls earlier runs made ({@link DumJobCall}), and every
 * call it grants is written back at once, so the daily, weekly and monthly caps hold across runs and
 * survive a run that fails half-way. The adapter is told which ids earlier runs fetched, and any
 * {@code cursor} it leaves on the source is saved after the fetch.
 *
 * <p>
 * <strong>Cross-source deduplication (trabalharei TRA23).</strong> With a {@link DumJobDeduplicator}
 * the run does not publish its postings under their own ids: {@link #discover()} hands the open
 * postings to the deduplicator, which records them in the fingerprint index and answers one
 * {@code job-<hash>} document per group of duplicates &mdash; across every source and every run &mdash;
 * carrying the preferred member's posting. {@link #posting(String)} and {@link #extract(String)} then
 * answer those document ids. Without one (the constructors tests use) postings keep their own ids.
 *
 * <p>
 * The fetched set is memoized on the instance: the indexing loop resolves a source once, then calls
 * {@code discover()} followed by many {@code extract()} calls on the same instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class DumJobsStructuredSource implements DumStructuredSource {

    /** How long a posting stays live when the partner gives no expiry date. */
    public static final Duration DEFAULT_TTL = Duration.ofDays(30);

    /**
     * Fields {@link #extract} leaves to their canonical enricher: place (trabalharei TRA20), salary,
     * contract and seniority (TRA21), and occupation (TRA22).
     */
    static final Set<String> ENRICHED_FIELDS = Set.of(DumJobsSchema.CITY, DumJobsSchema.STATE,
            DumJobsSchema.CONTRACT, DumJobsSchema.SENIORITY, DumJobsSchema.SALARY_MIN,
            DumJobsSchema.SALARY_MAX, DumJobsSchema.SALARY_PERIOD, DumJobsSchema.SALARY_BAND,
            DumJobsSchema.OCCUPATION);

    /**
     * What {@link #extract} hands the enrichers as record transients (roadmap <strong>D117</strong>):
     * the adapter's values for the {@link #ENRICHED_FIELDS} and the partner's raw place and salary text.
     */
    static final Set<String> ENRICHER_INPUTS = union(ENRICHED_FIELDS,
            Set.of(DumJobSourceAdapter.LOCATION_RAW, DumJobSourceAdapter.SALARY_RAW));

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> all = new HashSet<>(first);
        all.addAll(second);
        return Set.copyOf(all);
    }

    private final DumJobSource source;
    private final DumStructuredSchema schema;
    private final DumJobSourceAdapter adapter;
    private final DumJobSeenRepository seenRepository;
    private final DumJobCallRepository callRepository;
    private final DumJobSourceRepository sourceRepository;
    private final Clock clock;
    private final DumJobDeduplicator deduplicator;
    private final DumJobLinkChecker linkChecker;
    private final DumJobTombstones tombstones;

    private Map<String, Map<String, Object>> records;
    /** Why this run's fetch stopped short, as the adapter noted it (D116). */
    private List<String> notes = List.of();
    /** Postings read before {@link #discover()} (indexById), read once from the store. */
    private final Map<String, Map<String, Object>> adhoc = new LinkedHashMap<>();

    public DumJobsStructuredSource(DumJobSource source, DumStructuredSchema schema,
            DumJobSourceAdapter adapter, DumJobSeenRepository seenRepository,
            DumJobCallRepository callRepository, DumJobSourceRepository sourceRepository, Clock clock) {
        this(source, schema, adapter, seenRepository, callRepository, sourceRepository, clock, null);
    }

    /**
     * A source whose run publishes one document per group of duplicates (trabalharei
     * <strong>TRA23</strong>); a {@code null} deduplicator publishes every posting on its own id.
     */
    public DumJobsStructuredSource(DumJobSource source, DumStructuredSchema schema,
            DumJobSourceAdapter adapter, DumJobSeenRepository seenRepository,
            DumJobCallRepository callRepository, DumJobSourceRepository sourceRepository, Clock clock,
            DumJobDeduplicator deduplicator) {
        this(source, schema, adapter, seenRepository, callRepository, sourceRepository, clock, deduplicator,
                null, null);
    }

    /**
     * A source that also checks apply links and queues tombstones (trabalharei <strong>TRA24</strong>);
     * either may be {@code null} to leave that step out.
     */
    public DumJobsStructuredSource(DumJobSource source, DumStructuredSchema schema,
            DumJobSourceAdapter adapter, DumJobSeenRepository seenRepository,
            DumJobCallRepository callRepository, DumJobSourceRepository sourceRepository, Clock clock,
            DumJobDeduplicator deduplicator, DumJobLinkChecker linkChecker, DumJobTombstones tombstones) {
        this.deduplicator = deduplicator;
        this.linkChecker = linkChecker;
        this.tombstones = tombstones;
        this.source = source;
        this.schema = schema;
        this.adapter = adapter;
        this.seenRepository = seenRepository;
        this.callRepository = callRepository;
        this.sourceRepository = sourceRepository;
        this.clock = clock;
    }

    @Override
    public String getSourceName() {
        return source.getName();
    }

    @Override
    public String getProviderName() {
        return DumJobsPlugin.PROVIDER_NAME;
    }

    @Override
    public Collection<String> getSiteNames() {
        return source.getTurSNSites();
    }

    @Override
    public DumStructuredSchema schema() {
        return schema;
    }

    @Override
    public Locale getLocale() {
        return localeOf(source.getLocale());
    }

    @Override
    public List<String> discover() {
        return new ArrayList<>(load().keySet());
    }

    /**
     * Why this run's fetch stopped short (trabalharei, roadmap <strong>D116</strong>): a call budget
     * refused, a page failed, a partner answered HTTP 429. The run record shows them (D96). The run is
     * still complete: {@link #discover()} replays every stored open posting, so a fetch that stopped
     * early lists fewer new postings but drops none, and the sweep that removes expired ones stays on.
     */
    @Override
    public List<String> runNotes() {
        return notes;
    }

    /**
     * Extract one posting. The {@link #ENRICHED_FIELDS} are left to their enrichers — place
     * (trabalharei <strong>TRA20</strong>), salary, contract and seniority (<strong>TRA21</strong>),
     * occupation (<strong>TRA22</strong>) —
     * which read the adapter's values from the record's transients ({@link #ENRICHER_INPUTS}, D117): a
     * D04 enricher can override a record value but never remove one (D98), and each canonical step must
     * be able to drop a value, such as a city that is not in its UF or a contract value that names two
     * kinds.
     */
    @Override
    public Optional<DumStructuredRecord> extract(String contentId) {
        String id = documentId(contentId);
        Map<String, Object> values = posting(id);
        if (values == null) {
            return Optional.empty();
        }
        DumStructuredRecord.Builder rec = DumStructuredRecord.builder(id, schema);
        for (String field : DumJobsSchema.FIELDS) {
            Object value = values.get(field);
            if (value == null || !schema.hasField(field) || ENRICHED_FIELDS.contains(field)) {
                continue;
            }
            try {
                rec.set(field, value);
            } catch (IllegalArgumentException e) {
                // One malformed value (e.g. a non-numeric salary) drops that field, not the posting.
                log.warn("Job '{}' in source '{}': {} — field dropped", contentId, source.getName(),
                        e.getMessage());
            }
        }
        // D117: what the canonical steps read travels on the record as transients, never indexed.
        for (String key : ENRICHER_INPUTS) {
            Object value = values.get(key);
            if (value != null) {
                rec.transientValue(key, value);
            }
        }
        return Optional.of(rec.build());
    }

    /**
     * The posting {@code contentId} as the adapter returned it — every key, including record keys that
     * are not schema fields ({@link DumJobSourceAdapter#LOCATION_RAW},
     * {@link DumJobSourceAdapter#SALARY_RAW}) — or {@code null} when it is
     * neither in this run nor in the store. After {@link #discover()} it is this run's live map; before
     * it, a copy is read once from the store &mdash; for a group id ({@code job-<hash>}, TRA23) from the
     * fingerprint index &mdash; and kept for the instance. The enrichers read the record's transients
     * instead (D117); this is the source's own view.
     */
    Map<String, Object> posting(String contentId) {
        if (records != null) {
            return records.get(contentId);
        }
        return adhoc.computeIfAbsent(contentId, id -> deduplicator != null && DumJobFingerprint.isGroupId(id)
                ? deduplicator.groupPosting(id, clock.instant()).orElse(null)
                : storedPosting(id));
    }

    /**
     * The document an id is published under: itself, except a posting id asked before
     * {@link #discover()} (indexById) whose posting belongs to a group, which answers the group id.
     */
    private String documentId(String contentId) {
        if (records != null || deduplicator == null || DumJobFingerprint.isGroupId(contentId)) {
            return contentId;
        }
        return deduplicator.groupIdOf(source.getId(), contentId).orElse(contentId);
    }

    /** What this run saw of each posting, for the expiry, link check, tombstones and summary. */
    private static final class Run {
        /** Postings open (published) after the last run: payload and expiry, by id. */
        final Map<String, Prior> openBefore = new HashMap<>();
        /** Every id with a row before this run, dead-linked ones included. */
        final Set<String> known = new HashSet<>();
        /** The row behind every open posting of this run, by id. */
        final Map<String, DumJobSeen> rows = new HashMap<>();
        /** The latest payload and expiry of every posting this run touched. */
        final Map<String, Prior> latest = new HashMap<>();
        int fetched;
        int fresh;
        int kept;
        int checked;
        int dead;
        int retry;
        int queued;
    }

    /** A posting as a run left it: its payload and expiry. */
    private record Prior(String payload, Instant expiresAt) {
    }

    /** Fetch once, record what was fetched, and merge in every stored posting still open. */
    private synchronized Map<String, Map<String, Object>> load() {
        if (records != null) {
            return records;
        }
        Instant now = clock.instant();
        Run run = new Run();
        snapshot(run);
        Map<String, Map<String, Object>> live = new LinkedHashMap<>();
        Map<String, Instant> expiries = new LinkedHashMap<>();
        DumJobFetchContext context = new DumJobFetchContext(source, budget(now), this::seenBefore,
                jobId -> firstSeen(jobId, now));
        String cursorBefore = source.getCursor();
        List<Map<String, Object>> postings;
        try {
            postings = adapter.fetch(context);
        } finally {
            saveCursor(cursorBefore);
            notes = context.notes();
        }
        for (Map<String, Object> raw : postings) {
            ingest(raw, now, live, expiries, run);
        }
        prune(now);
        for (DumJobSeen seen : seenRepository.findBySourceIdAndExpiresAtAfter(source.getId(), now)) {
            if (!live.containsKey(seen.getJobId()) && seen.getPayload() != null) {
                live.put(seen.getJobId(), new JSONObject(seen.getPayload()).toMap());
                expiries.put(seen.getJobId(), seen.getExpiresAt());
                run.rows.put(seen.getJobId(), seen);
                run.kept++;
            }
        }
        checkLinks(live, expiries, run, now);
        List<String> expired = run.openBefore.keySet().stream().filter(id -> !live.containsKey(id)).sorted()
                .toList();
        queuePostingTombstones(expired, run, now);
        if (deduplicator == null) {
            records = live;
        } else {
            DumJobDeduplicator.Result result = deduplicate(live, expiries, now);
            records = result.documents();
            if (tombstones != null) {
                for (DumJobDeduplicator.Vanished gone : result.vanished()) {
                    tombstones.queue(gone.documentId(), gone.posting(), gone.expiredAt());
                    run.queued++;
                }
            }
        }
        if (tombstones != null) {
            tombstones.cancel(records.keySet());
        }
        logRun(run, live.size(), expired.size());
        return records;
    }

    /** Which postings were open after the last run; for an undated source, re-date stored expiries. */
    private void snapshot(Run run) {
        for (DumJobSeen seen : seenRepository.findBySourceId(source.getId())) {
            run.known.add(seen.getJobId());
            if (seen.getLinkDeadAt() != null) {
                continue;
            }
            if (!adapter.datedPostings() && seen.getPayload() != null && seen.getLastSeenAt() != null) {
                // Rows stored before TRA24 counted an undated posting from its first fetch.
                Instant rule = expiresAt(new JSONObject(seen.getPayload()), seen.getLastSeenAt(), false);
                if (!rule.equals(seen.getExpiresAt())) {
                    seen.setExpiresAt(rule);
                    seenRepository.save(seen);
                }
            }
            run.openBefore.put(seen.getJobId(), new Prior(seen.getPayload(), seen.getExpiresAt()));
        }
    }

    /**
     * Delete the expired rows: at once for an ordinary row, and 30 days after the partner last returned
     * it for a dead-linked one (kept until then so a re-fetch does not revive it).
     */
    private void prune(Instant now) {
        List<DumJobSeen> gone = seenRepository
                .findBySourceIdAndExpiresAtLessThanEqual(source.getId(), now).stream()
                .filter(seen -> seen.getLinkDeadAt() == null || seen.getLastSeenAt() == null
                        || !seen.getLastSeenAt().plus(DEFAULT_TTL).isAfter(now))
                .toList();
        if (!gone.isEmpty()) {
            seenRepository.deleteAll(gone);
        }
    }

    /**
     * HEAD-check a sample of the open postings' apply links, least recently checked first; a dead one
     * leaves {@code live} and its row is expired now. A RETRY leaves the row as it was.
     */
    private void checkLinks(Map<String, Map<String, Object>> live, Map<String, Instant> expiries, Run run,
            Instant now) {
        if (linkChecker == null || !adapter.checkApplyLinks()) {
            return;
        }
        List<DumJobSeen> sample = run.rows.entrySet().stream()
                .filter(row -> live.containsKey(row.getKey()) && applyUrl(live.get(row.getKey())) != null)
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(DumJobSeen::getLinkCheckedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(DumJobSeen::getJobId))
                .limit(linkChecker.sampleSize(source))
                .toList();
        for (DumJobSeen seen : sample) {
            String id = seen.getJobId();
            DumJobLinkChecker.Verdict verdict = linkChecker.check(applyUrl(live.get(id)));
            run.checked++;
            switch (verdict) {
                case RETRY -> run.retry++;
                case ALIVE -> {
                    seen.setLinkCheckedAt(now);
                    seenRepository.save(seen);
                }
                case DEAD -> {
                    run.dead++;
                    seen.setLinkCheckedAt(now);
                    seen.setLinkDeadAt(now);
                    seen.setExpiresAt(now);
                    seenRepository.save(seen);
                    run.latest.put(id, new Prior(seen.getPayload(), now));
                    live.remove(id);
                    expiries.remove(id);
                    log.info("Job source '{}': the apply link of '{}' is gone — posting expired",
                            source.getName(), id);
                }
            }
        }
    }

    /**
     * Queue a tombstone for each expired posting that was published on its own id: every one without a
     * deduplicator, else only one without a fingerprint (a fingerprinted posting belonged to a group,
     * whose document the deduplicator tracks).
     */
    private void queuePostingTombstones(List<String> expired, Run run, Instant now) {
        if (tombstones == null) {
            return;
        }
        for (String id : expired) {
            Prior prior = run.latest.getOrDefault(id, run.openBefore.get(id));
            Map<String, Object> payload = prior.payload() == null ? Map.of()
                    : new JSONObject(prior.payload()).toMap();
            if (deduplicator != null && deduplicator.fingerprint(payload).isPresent()) {
                continue;
            }
            Instant at = prior.expiresAt() == null || prior.expiresAt().isAfter(now) ? now : prior.expiresAt();
            tombstones.queue(id, payload, at);
            run.queued++;
        }
    }

    private void logRun(Run run, int active, int expired) {
        String links;
        if (linkChecker == null) {
            links = "link check off";
        } else if (!adapter.checkApplyLinks()) {
            links = "link check off for " + adapter.id() + " links";
        } else {
            links = "%d link(s) checked, %d dead, %d to retry".formatted(run.checked, run.dead, run.retry);
        }
        log.info("Job source '{}' run: {} active, {} new, {} expired ({} by dead link); {} fetched, {} kept "
                + "from earlier runs; {}; {} tombstone(s) queued", source.getName(), active, run.fresh, expired,
                run.dead, run.fetched, run.kept, links, run.queued);
    }

    private static String applyUrl(Map<String, Object> posting) {
        Object value = posting == null ? null : posting.get(DumJobsSchema.APPLY_URL);
        if (value instanceof Collection<?> values) {
            value = values.isEmpty() ? null : values.iterator().next();
        }
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    /** The run's documents: one per group of duplicates, across sources and runs (TRA23). */
    private DumJobDeduplicator.Result deduplicate(Map<String, Map<String, Object>> live,
            Map<String, Instant> expiries, Instant now) {
        Map<String, DumJobDeduplicator.Posting> postings = new LinkedHashMap<>();
        live.forEach((id, payload) -> postings.put(id, new DumJobDeduplicator.Posting(payload,
                expiries.getOrDefault(id, now.plus(DEFAULT_TTL)))));
        return deduplicator.dedupe(source, adapter.id(), postings, now);
    }

    /**
     * This run's budget: the source's caps (the adapter's plan caps where the source sets none),
     * seeded with the calls of earlier runs still inside the longest window, and persisting every
     * call it grants. Calls past {@link DumJobCall#RETENTION_DAYS} are pruned first.
     */
    private DumJobCallBudget budget(Instant now) {
        String sourceId = source.getId();
        callRepository.deleteBySourceIdAndCalledAtLessThanEqual(sourceId,
                now.minus(Duration.ofDays(DumJobCall.RETENTION_DAYS)));
        List<Instant> prior = callRepository
                .findBySourceIdAndCalledAtAfter(sourceId, now.minus(DumJobCallBudget.Window.MONTH.length()))
                .stream().map(DumJobCall::getCalledAt).toList();
        return DumJobCallBudget.builder(clock)
                .caps(source, adapter.defaultCaps())
                .priorCalls(prior)
                .onGranted(at -> callRepository
                        .save(DumJobCall.builder().sourceId(sourceId).calledAt(at).build()))
                .build();
    }

    /** Whether an earlier run fetched {@code jobId} (this run's postings are stored after the fetch). */
    private boolean seenBefore(String jobId) {
        return seenRepository.findBySourceIdAndJobId(source.getId(), jobId).isPresent();
    }

    /**
     * When an earlier run first fetched {@code jobId}, else {@code now} (this run is the first). A row
     * written before {@code firstSeenAt} existed answers its {@code lastSeenAt}, the earliest time
     * recorded for it.
     */
    private Instant firstSeen(String jobId, Instant now) {
        return seenRepository.findBySourceIdAndJobId(source.getId(), jobId)
                .map(DumJobsStructuredSource::firstSeenOf)
                .orElse(now);
    }

    private static Instant firstSeenOf(DumJobSeen seen) {
        return seen.getFirstSeenAt() != null ? seen.getFirstSeenAt() : seen.getLastSeenAt();
    }

    /** Persist the cursor the adapter left on the source, onto the stored row only. */
    private void saveCursor(String before) {
        String after = source.getCursor();
        if (Objects.equals(before, after)) {
            return;
        }
        sourceRepository.findById(source.getId()).ifPresent(stored -> {
            stored.setCursor(after);
            sourceRepository.save(stored);
        });
    }

    /**
     * Record one fetched posting in the seen-id store and, when it is still open, add it to
     * {@code live}. Even an already-expired posting is remembered: its row then carries the past
     * expiry, so the prune drops it instead of an older copy republishing it. A posting whose apply
     * link was found dead stays expired whatever the partner says.
     */
    private void ingest(Map<String, Object> raw, Instant now, Map<String, Map<String, Object>> live,
            Map<String, Instant> expiries, Run run) {
        String id = idOf(raw);
        if (id == null) {
            log.warn("Job source '{}' returned a posting without an id — skipped", source.getName());
            return;
        }
        JSONObject payload = new JSONObject(raw);
        DumJobSeen seen = remember(id, payload, expiresAt(payload, now, adapter.datedPostings()), now);
        run.latest.put(id, new Prior(seen.getPayload(), seen.getExpiresAt()));
        if (!seen.getExpiresAt().isAfter(now)) {
            return;
        }
        live.put(id, payload.toMap());
        expiries.put(id, seen.getExpiresAt());
        run.rows.put(id, seen);
        run.fetched++;
        if (!run.known.contains(id)) {
            run.fresh++;
        }
    }

    /** An {@code extract} without a prior {@code discover} (indexById): read the store, never the API. */
    private Map<String, Object> storedPosting(String contentId) {
        Instant now = clock.instant();
        return seenRepository.findBySourceIdAndJobId(source.getId(), contentId)
                .filter(seen -> seen.getExpiresAt().isAfter(now) && seen.getPayload() != null)
                .map(seen -> new JSONObject(seen.getPayload()).toMap())
                .orElse(null);
    }

    private DumJobSeen remember(String id, JSONObject payload, Instant expiresAt, Instant now) {
        DumJobSeen seen = seenRepository.findBySourceIdAndJobId(source.getId(), id)
                .orElseGet(() -> DumJobSeen.builder().sourceId(source.getId()).jobId(id).build());
        if (seen.getFirstSeenAt() == null) {
            Instant first = firstSeenOf(seen);
            seen.setFirstSeenAt(first != null ? first : now);
        }
        seen.setPayload(payload.toString());
        seen.setExpiresAt(seen.getLinkDeadAt() != null ? seen.getLinkDeadAt() : expiresAt);
        seen.setLastSeenAt(now);
        seenRepository.save(seen);
        return seen;
    }

    private static String idOf(Map<String, Object> raw) {
        Object id = raw.get(DumJobsSchema.ID);
        if (id == null || id.toString().isBlank()) {
            return null;
        }
        return id.toString();
    }

    /**
     * The posting's own {@code expiresAt} when given and parsable; else, for a dated partner,
     * {@code publishedAt} plus {@link #DEFAULT_TTL}; else {@code lastSeen} (the run that last fetched
     * it) plus the TTL &mdash; always so for an undated partner, whose {@code publishedAt} is only the
     * first fetch.
     */
    static Instant expiresAt(JSONObject payload, Instant lastSeen, boolean dated) {
        Instant explicit = toInstant(payload.opt(DumJobsSchema.EXPIRES_AT));
        if (explicit != null) {
            return explicit;
        }
        Instant published = dated ? toInstant(payload.opt(DumJobsSchema.PUBLISHED_AT)) : null;
        return (published != null ? published : lastSeen).plus(DEFAULT_TTL);
    }

    /** Read a date value as an instant: a {@code java.time} value or ISO-8601 text. */
    public static Instant toInstant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case OffsetDateTime odt -> odt.toInstant();
            case ZonedDateTime zdt -> zdt.toInstant();
            case LocalDateTime ldt -> ldt.toInstant(ZoneOffset.UTC);
            case LocalDate ld -> ld.atStartOfDay(ZoneOffset.UTC).toInstant();
            default -> parse(value.toString().trim());
        };
    }

    private static Instant parse(String text) {
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            // fall through to the less specific forms
        }
        try {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            // fall through
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static Locale localeOf(String locale) {
        if (locale == null || locale.isBlank()) {
            return Locale.of("pt", "BR");
        }
        String[] parts = locale.split("[_-]", 2);
        return parts.length == 2 ? Locale.of(parts[0], parts[1]) : Locale.of(parts[0]);
    }
}
