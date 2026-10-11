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

package com.viglet.dumont.connector.plugin.jobs.source.dedupe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobFingerprintEntry;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobSource;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobFingerprintRepository;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationResolver;
import com.viglet.dumont.connector.plugin.jobs.source.salary.DumJobSalaryEnricher;

import lombok.extern.slf4j.Slf4j;

/**
 * Cross-source, cross-run deduplication of job postings (trabalharei <strong>TRA23</strong>): the
 * same opening reached through several partners is published once.
 *
 * <p>
 * <strong>Why not D18.</strong> Dumont's entity resolution ({@code DumEntityResolver}) only runs in
 * {@code crawl()}, holds one call's records in memory, and merges field by field with the first
 * contributor winning. The jobs connector is driven per source through {@code indexAll(source)}, so a
 * later source's run must find what an earlier source's run fetched, and the published record must be
 * the <em>preferred</em> member whole ({@link DumJobPreference}). So the index is kept here, in the
 * {@code job_fingerprint} table ({@link DumJobFingerprintEntry}), and a source's run consults it from
 * {@code discover()}.
 *
 * <p>
 * <strong>One document per group.</strong> {@link #dedupe} replaces a source's open postings by the
 * groups they fall in: each group is published as {@code job-<fingerprint hash>} with the preferred
 * open member's posting, whichever source holds it. The id depends only on the fingerprint, so it is
 * the same in every run and whichever source arrives first.
 *
 * <p>
 * <strong>The sweep.</strong> Dumont tracks what a run published per {@code (objectId, source)}, and a
 * full run deindexes every id <em>its own source</em> tracked but did not publish again &mdash; through
 * a delete job that removes the document from the index whoever else still tracks it. Had only the
 * preferred member's source published the group, a group whose winner moved from Adzuna to Jooble
 * would be deleted by Adzuna's next sweep, and Jooble's next run would not resend it (its checksum is
 * unchanged). So <em>every</em> source with a row in a group publishes the group document while any
 * member of the group is open &mdash; including a source whose own posting has expired, whose row is
 * kept (expired) for exactly that. The document leaves the index only once the whole group has
 * expired: the rows are then deleted, no source publishes it again, and the first sweep after removes
 * it. The cost is that a group document is sent once per source that holds it, which Dumont's
 * checksum makes a no-op when unchanged.
 *
 * <p>
 * <strong>Duplicate rate.</strong> Each {@link #dedupe} records, per source run, how many of its open
 * postings share a group with an open posting of each other source; {@link #logSummary} prints it as
 * one line (the run record cannot carry it: D96, D100).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Slf4j
@Component
public class DumJobDeduplicator {

    /** How many fingerprints one {@code IN} query carries. */
    static final int QUERY_CHUNK = 500;

    /** One open posting of a run: the adapter record and when it expires. */
    public record Posting(Map<String, Object> payload, Instant expiresAt) {
    }

    /**
     * What a source publishes after deduplication: document id &rarr; posting, in run order. A group
     * document's posting is a copy of the preferred member's, with {@code id} and the site
     * {@code url} rewritten to the group id. Postings without a fingerprint keep their own id.
     */
    public record Result(Map<String, Map<String, Object>> documents, Summary summary) {
    }

    /**
     * One source run's duplicate counts.
     *
     * @param postings        the source's open postings
     * @param documents       the documents it publishes (groups plus unfingerprinted postings)
     * @param unfingerprinted postings published on their own id (no title left, no date)
     * @param withinSource    postings folded into another posting of the same source
     * @param held            groups published only because the source once held a member (its own
     *                        has expired) &mdash; kept so its sweep does not deindex them
     * @param sharedWith      per other source name, how many of this source's open postings share a
     *                        group with an open posting of it
     */
    public record Summary(int postings, int documents, int unfingerprinted, int withinSource, int held,
            Map<String, Integer> sharedWith) {

        /** No run recorded. */
        public static final Summary EMPTY = new Summary(0, 0, 0, 0, 0, Map.of());
    }

    private final DumJobFingerprintRepository repository;
    private final DumJobLocationEnricher place;
    private final Map<String, Summary> summaries = new ConcurrentHashMap<>();

    @Autowired
    public DumJobDeduplicator(DumJobFingerprintRepository repository) {
        this(repository, DumJobLocationEnricher.standard());
    }

    public DumJobDeduplicator(DumJobFingerprintRepository repository, DumJobLocationEnricher place) {
        this.repository = repository;
        this.place = place;
    }

    /** The fingerprint of an adapter posting, or empty when it has none (see {@link DumJobFingerprint}). */
    public Optional<DumJobFingerprint.Key> fingerprint(Map<String, Object> posting) {
        DumJobLocationResolver.Resolution where = place.place(posting);
        return DumJobFingerprint.of(text(posting.get(DumJobsSchema.TITLE)),
                text(posting.get(DumJobsSchema.COMPANY)), where.city(), where.uf(), where.remote(),
                first(posting.get(DumJobsSchema.PUBLISHED_AT)));
    }

    /**
     * Record {@code source}'s open postings in the fingerprint index and answer what the run publishes:
     * one document per group any of its postings &mdash; open now, or open in an earlier run &mdash;
     * belongs to, while that group has an open member.
     *
     * @param source     the job source running
     * @param sourceType its adapter type ({@code adzuna}, {@code jooble}, ...)
     * @param live       every open posting of the source this run, by posting id, in run order
     * @param now        the run's clock
     */
    public Result dedupe(DumJobSource source, String sourceType, Map<String, Posting> live, Instant now) {
        Run run = new Run(source, live, now);
        index(run, sourceType);
        Set<String> fingerprints = new LinkedHashSet<>(run.groupOfPosting.values());
        Set<String> heldCandidates = new LinkedHashSet<>();
        for (DumJobFingerprintEntry entry : repository.findBySourceId(source.getId())) {
            if (fingerprints.add(entry.getFingerprint())) {
                heldCandidates.add(entry.getFingerprint());
            }
        }
        Map<String, List<DumJobFingerprintEntry>> groups = load(fingerprints);
        publishOpen(run, groups);
        publishHeld(run, groups, heldCandidates);
        Summary summary = new Summary(live.size(), run.documents.size(), run.unfingerprinted,
                run.withinSource, run.held, run.sharedWith);
        summaries.put(source.getName(), summary);
        return new Result(run.documents, summary);
    }

    /** One source run being deduplicated. */
    private static final class Run {
        final DumJobSource source;
        final Map<String, Posting> live;
        final Instant now;
        final Map<String, String> groupOfPosting = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
        final Set<String> published = new LinkedHashSet<>();
        final Map<String, Integer> sharedWith = new TreeMap<>();
        int unfingerprinted;
        int withinSource;
        int held;

        Run(DumJobSource source, Map<String, Posting> live, Instant now) {
            this.source = source;
            this.live = live;
            this.now = now;
        }
    }

    /**
     * Write the run's postings into the index: each open posting's row under its fingerprint, and
     * every other row of the source expired &mdash; kept, so the source still publishes its group
     * while another member is open.
     */
    private void index(Run run, String sourceType) {
        Map<String, DumJobFingerprintEntry> own = new HashMap<>();
        repository.findBySourceId(run.source.getId()).forEach(entry -> own.put(entry.getJobId(), entry));
        List<DumJobFingerprintEntry> writes = new ArrayList<>();
        for (Map.Entry<String, Posting> posting : run.live.entrySet()) {
            String jobId = posting.getKey();
            DumJobFingerprintEntry existing = own.remove(jobId);
            Optional<DumJobFingerprint.Key> key = fingerprint(posting.getValue().payload());
            if (key.isEmpty()) {
                // Published on its own id; a row it had stays (expired below) to hold its old group.
                run.unfingerprinted++;
                if (existing != null) {
                    own.put(jobId, existing);
                }
                continue;
            }
            String hash = key.get().hash();
            run.groupOfPosting.put(jobId, hash);
            if (existing != null && !hash.equals(existing.getFingerprint())) {
                // The posting moved to another group (its title or place changed): an expired row keeps
                // this source publishing the old group while that has open members.
                own.computeIfAbsent(jobId + "@" + existing.getFingerprint(),
                        holder -> tombstone(existing, holder, run.now));
            }
            writes.add(member(existing, run.source, sourceType, jobId, hash, posting.getValue(), run.now));
        }
        for (DumJobFingerprintEntry stale : own.values()) {
            boolean open = stale.getExpiresAt().isAfter(run.now);
            if (open) {
                stale.setExpiresAt(run.now);
            }
            if (open || stale.getId() == null) {
                writes.add(stale);
            }
        }
        repository.saveAll(writes);
    }

    /** One document per group of the run's open postings, in run order, counting the duplicates. */
    private static void publishOpen(Run run, Map<String, List<DumJobFingerprintEntry>> groups) {
        for (Map.Entry<String, Posting> posting : run.live.entrySet()) {
            String jobId = posting.getKey();
            String hash = run.groupOfPosting.get(jobId);
            List<DumJobFingerprintEntry> members = hash == null ? List.of() : open(groups.get(hash), run.now);
            if (members.isEmpty()) {
                // No fingerprint (or, impossibly, no open member): the posting is published alone.
                run.documents.put(jobId, posting.getValue().payload());
                continue;
            }
            if (run.published.add(hash)) {
                run.documents.put(DumJobFingerprint.GROUP_PREFIX + hash, groupPosting(hash, members, run.live));
            } else {
                run.withinSource++;
            }
            countShared(run, members);
        }
    }

    /**
     * The groups the source holds only through expired rows: published while another member is open;
     * their rows deleted from the index once none is.
     */
    private void publishHeld(Run run, Map<String, List<DumJobFingerprintEntry>> groups,
            Set<String> heldCandidates) {
        List<DumJobFingerprintEntry> dead = new ArrayList<>();
        for (String hash : heldCandidates) {
            List<DumJobFingerprintEntry> rows = groups.getOrDefault(hash, List.of());
            List<DumJobFingerprintEntry> members = open(rows, run.now);
            if (members.isEmpty()) {
                dead.addAll(rows);
            } else if (run.published.add(hash)) {
                run.documents.put(DumJobFingerprint.GROUP_PREFIX + hash, groupPosting(hash, members, run.live));
                run.held++;
            }
        }
        if (!dead.isEmpty()) {
            repository.deleteAll(dead);
        }
    }

    /**
     * The posting published as group document {@code groupId} now, read from the index alone &mdash;
     * for an {@code extract} without a {@code discover} (indexById). Empty when the group is unknown
     * or has no open member.
     */
    public Optional<Map<String, Object>> groupPosting(String groupId, Instant now) {
        String hash = DumJobFingerprint.hashOf(groupId);
        if (hash == null) {
            return Optional.empty();
        }
        List<DumJobFingerprintEntry> members = open(repository.findByFingerprintIn(List.of(hash)), now);
        return members.isEmpty() ? Optional.empty()
                : Optional.of(groupPosting(hash, members, Map.of()));
    }

    /** The group document id posting {@code jobId} of {@code sourceId} is published under, if any. */
    public Optional<String> groupIdOf(String sourceId, String jobId) {
        return repository.findBySourceIdAndJobId(sourceId, jobId)
                .map(entry -> DumJobFingerprint.GROUP_PREFIX + entry.getFingerprint());
    }

    /** This source's counts from its last {@link #dedupe}, without clearing them. */
    public Summary summary(String sourceName) {
        return summaries.getOrDefault(sourceName, Summary.EMPTY);
    }

    /** Forget this source's counts &mdash; called when a run starts. */
    public void reset(String sourceName) {
        summaries.remove(sourceName);
    }

    /**
     * Log one duplicate-rate line for the source's run and clear its counts. The per-pair counts go
     * nowhere else: a structured run cannot report notes (D96) and enrichers have no end-of-run hook
     * (D100).
     */
    public Summary logSummary(String sourceName) {
        Summary summary = summaries.remove(sourceName);
        if (summary == null) {
            return Summary.EMPTY;
        }
        String pairs = summary.sharedWith().isEmpty() ? "none"
                : summary.sharedWith().entrySet().stream()
                        .map(pair -> "%s %d (%s)".formatted(pair.getKey(), pair.getValue(),
                                percent(pair.getValue(), summary.postings())))
                        .collect(Collectors.joining(", "));
        log.info("Job source '{}' duplicates: {} open posting(s) published as {} document(s); shared with "
                + "{}; {} within the source; {} without a fingerprint; {} group(s) kept for other sources",
                sourceName, summary.postings(), summary.documents(), pairs, summary.withinSource(),
                summary.unfingerprinted(), summary.held());
        return summary;
    }

    private Map<String, List<DumJobFingerprintEntry>> load(Collection<String> fingerprints) {
        Map<String, List<DumJobFingerprintEntry>> groups = new HashMap<>();
        List<String> all = new ArrayList<>(fingerprints);
        for (int from = 0; from < all.size(); from += QUERY_CHUNK) {
            List<String> chunk = all.subList(from, Math.min(all.size(), from + QUERY_CHUNK));
            for (DumJobFingerprintEntry entry : repository.findByFingerprintIn(chunk)) {
                groups.computeIfAbsent(entry.getFingerprint(), fp -> new ArrayList<>()).add(entry);
            }
        }
        return groups;
    }

    private static List<DumJobFingerprintEntry> open(List<DumJobFingerprintEntry> rows, Instant now) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().filter(entry -> entry.getExpiresAt().isAfter(now))
                .sorted(DumJobPreference.ORDER).toList();
    }

    /**
     * The preferred member's posting re-keyed as the group document. A member of the running source
     * is read from this run's posting, any other from the payload its own run stored.
     */
    private static Map<String, Object> groupPosting(String hash, List<DumJobFingerprintEntry> members,
            Map<String, Posting> live) {
        DumJobFingerprintEntry winner = members.getFirst();
        Posting own = live.get(winner.getJobId());
        Map<String, Object> posting;
        if (own != null) {
            posting = new LinkedHashMap<>(own.payload());
        } else if (winner.getPayload() != null) {
            posting = new JSONObject(winner.getPayload()).toMap();
        } else {
            posting = new LinkedHashMap<>();
        }
        String groupId = DumJobFingerprint.GROUP_PREFIX + hash;
        posting.put(DumJobsSchema.ID, groupId);
        Object url = posting.get(DumJobsSchema.URL);
        if (url instanceof String text && text.endsWith("-" + winner.getJobId() + "/")) {
            posting.put(DumJobsSchema.URL,
                    text.substring(0, text.length() - winner.getJobId().length() - 1) + groupId + "/");
        }
        return posting;
    }

    private static void countShared(Run run, List<DumJobFingerprintEntry> members) {
        members.stream()
                .filter(member -> !run.source.getId().equals(member.getSourceId()))
                .map(member -> member.getSourceName() != null ? member.getSourceName() : member.getSourceId())
                .distinct()
                .forEach(other -> run.sharedWith.merge(other, 1, Integer::sum));
    }

    private DumJobFingerprintEntry member(DumJobFingerprintEntry existing, DumJobSource source,
            String sourceType, String jobId, String hash, Posting posting, Instant now) {
        DumJobFingerprintEntry entry = existing != null ? existing
                : DumJobFingerprintEntry.builder().sourceId(source.getId()).jobId(jobId).build();
        Map<String, Object> payload = posting.payload();
        entry.setFingerprint(hash);
        entry.setSourceName(source.getName());
        entry.setSourceType(sourceType);
        entry.setApplyUrl(text(payload.get(DumJobsSchema.APPLY_URL)));
        entry.setHasSalary(DumJobSalaryEnricher.hasSalary(payload));
        String description = text(payload.get(DumJobsSchema.TEXT));
        entry.setDescriptionLength(description == null ? 0 : description.length());
        entry.setExpiresAt(posting.expiresAt());
        entry.setLastSeenAt(now);
        entry.setPayload(new JSONObject(payload).toString());
        return entry;
    }

    /** An expired copy of {@code entry} under a distinct posting key, holding its old group. */
    private static DumJobFingerprintEntry tombstone(DumJobFingerprintEntry entry, String holder,
            Instant now) {
        return entry.toBuilder().id(null).jobId(holder)
                .expiresAt(entry.getExpiresAt().isAfter(now) ? now : entry.getExpiresAt())
                .build();
    }

    private static String percent(int part, int whole) {
        return whole == 0 ? "0%" : String.format(Locale.ROOT, "%.1f%%", part * 100.0 / whole);
    }

    private static Object first(Object value) {
        if (value instanceof Collection<?> values) {
            return values.isEmpty() ? null : values.iterator().next();
        }
        return value;
    }

    private static String text(Object value) {
        Object one = first(value);
        if (one == null) {
            return null;
        }
        String text = one.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
