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
 * A posting expires at its own {@code expiresAt} when the partner gives one, else 30 days after its
 * {@code publishedAt}, else 30 days after the run that last saw it. Expired rows are pruned on each
 * discover, so the next full run deindexes them.
 *
 * <p>
 * Each run's call budget is seeded with the calls earlier runs made ({@link DumJobCall}), and every
 * call it grants is written back at once, so the daily, weekly and monthly caps hold across runs and
 * survive a run that fails half-way. The adapter is told which ids earlier runs fetched, and any
 * {@code cursor} it leaves on the source is saved after the fetch.
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
     * Fields {@link #extract} leaves to their canonical enricher: place (trabalharei TRA20), and
     * salary, contract and seniority (TRA21).
     */
    static final Set<String> ENRICHED_FIELDS = Set.of(DumJobsSchema.CITY, DumJobsSchema.STATE,
            DumJobsSchema.CONTRACT, DumJobsSchema.SENIORITY, DumJobsSchema.SALARY_MIN,
            DumJobsSchema.SALARY_MAX, DumJobsSchema.SALARY_PERIOD, DumJobsSchema.SALARY_BAND);

    private final DumJobSource source;
    private final DumStructuredSchema schema;
    private final DumJobSourceAdapter adapter;
    private final DumJobSeenRepository seenRepository;
    private final DumJobCallRepository callRepository;
    private final DumJobSourceRepository sourceRepository;
    private final Clock clock;

    private Map<String, Map<String, Object>> records;

    public DumJobsStructuredSource(DumJobSource source, DumStructuredSchema schema,
            DumJobSourceAdapter adapter, DumJobSeenRepository seenRepository,
            DumJobCallRepository callRepository, DumJobSourceRepository sourceRepository, Clock clock) {
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
     * Extract one posting. The {@link #ENRICHED_FIELDS} are left to their enrichers — place
     * (trabalharei <strong>TRA20</strong>), salary, contract and seniority (<strong>TRA21</strong>) —
     * which read the adapter's values from {@link #posting(String)}: a D04 enricher can override a
     * record value but never remove one (D98), and each canonical step must be able to drop a value,
     * such as a city that is not in its UF or a contract value that names two kinds.
     */
    @Override
    public Optional<DumStructuredRecord> extract(String contentId) {
        Map<String, Object> values = posting(contentId);
        if (values == null) {
            return Optional.empty();
        }
        DumStructuredRecord.Builder rec = DumStructuredRecord.builder(contentId, schema);
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
        return Optional.of(rec.build());
    }

    /**
     * The posting {@code contentId} as the adapter returned it — every key, including record keys that
     * are not schema fields ({@link DumJobSourceAdapter#LOCATION_RAW},
     * {@link DumJobSourceAdapter#SALARY_RAW}) — or {@code null} when it is
     * neither in this run nor in the store. After {@link #discover()} it is this run's live map, so an
     * {@link #annotate} shows up here; before it, a fresh copy is read from the store.
     */
    public Map<String, Object> posting(String contentId) {
        return records != null ? records.get(contentId) : storedPosting(contentId);
    }

    /**
     * Attach a record key that is not a schema field (e.g. {@link DumJobSourceAdapter#LOCATION_UNRESOLVED})
     * to a posting of this run. Never published; a no-op before {@link #discover()} or for an unknown id.
     */
    public void annotate(String contentId, String key, Object value) {
        Map<String, Object> posting = records != null ? records.get(contentId) : null;
        if (posting != null) {
            posting.put(key, value);
        }
    }

    /** Fetch once, record what was fetched, and merge in every stored posting still open. */
    private synchronized Map<String, Map<String, Object>> load() {
        if (records != null) {
            return records;
        }
        Instant now = clock.instant();
        Map<String, Map<String, Object>> live = new LinkedHashMap<>();
        int fetched = 0;
        DumJobFetchContext context = new DumJobFetchContext(source, budget(now), this::seenBefore,
                jobId -> firstSeen(jobId, now));
        String cursorBefore = source.getCursor();
        List<Map<String, Object>> postings;
        try {
            postings = adapter.fetch(context);
        } finally {
            saveCursor(cursorBefore);
        }
        for (Map<String, Object> raw : postings) {
            if (ingest(raw, now, live)) {
                fetched++;
            }
        }
        seenRepository.deleteBySourceIdAndExpiresAtLessThanEqual(source.getId(), now);
        int kept = 0;
        for (DumJobSeen seen : seenRepository.findBySourceIdAndExpiresAtAfter(source.getId(), now)) {
            if (!live.containsKey(seen.getJobId()) && seen.getPayload() != null) {
                live.put(seen.getJobId(), new JSONObject(seen.getPayload()).toMap());
                kept++;
            }
        }
        log.info("Job source '{}' discovered {} posting(s): {} fetched, {} kept from earlier runs",
                source.getName(), live.size(), fetched, kept);
        records = live;
        return records;
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
     * expiry, so the prune drops it instead of an older copy republishing it.
     */
    private boolean ingest(Map<String, Object> raw, Instant now, Map<String, Map<String, Object>> live) {
        String id = idOf(raw);
        if (id == null) {
            log.warn("Job source '{}' returned a posting without an id — skipped", source.getName());
            return false;
        }
        JSONObject payload = new JSONObject(raw);
        Instant expiresAt = expiresAt(payload, now);
        remember(id, payload, expiresAt, now);
        if (!expiresAt.isAfter(now)) {
            return false;
        }
        live.put(id, payload.toMap());
        return true;
    }

    /** An {@code extract} without a prior {@code discover} (indexById): read the store, never the API. */
    private Map<String, Object> storedPosting(String contentId) {
        Instant now = clock.instant();
        return seenRepository.findBySourceIdAndJobId(source.getId(), contentId)
                .filter(seen -> seen.getExpiresAt().isAfter(now) && seen.getPayload() != null)
                .map(seen -> new JSONObject(seen.getPayload()).toMap())
                .orElse(null);
    }

    private void remember(String id, JSONObject payload, Instant expiresAt, Instant now) {
        DumJobSeen seen = seenRepository.findBySourceIdAndJobId(source.getId(), id)
                .orElseGet(() -> DumJobSeen.builder().sourceId(source.getId()).jobId(id).build());
        if (seen.getFirstSeenAt() == null) {
            Instant first = firstSeenOf(seen);
            seen.setFirstSeenAt(first != null ? first : now);
        }
        seen.setPayload(payload.toString());
        seen.setExpiresAt(expiresAt);
        seen.setLastSeenAt(now);
        seenRepository.save(seen);
    }

    private static String idOf(Map<String, Object> raw) {
        Object id = raw.get(DumJobsSchema.ID);
        if (id == null || id.toString().isBlank()) {
            return null;
        }
        return id.toString();
    }

    /**
     * The posting's own {@code expiresAt} when given and parsable; else {@code publishedAt} plus
     * {@link #DEFAULT_TTL}; else {@code now} plus the TTL.
     */
    static Instant expiresAt(JSONObject payload, Instant now) {
        Instant explicit = toInstant(payload.opt(DumJobsSchema.EXPIRES_AT));
        if (explicit != null) {
            return explicit;
        }
        Instant published = toInstant(payload.opt(DumJobsSchema.PUBLISHED_AT));
        return (published != null ? published : now).plus(DEFAULT_TTL);
    }

    /** Read a date value as an instant: a {@code java.time} value or ISO-8601 text. */
    static Instant toInstant(Object value) {
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
