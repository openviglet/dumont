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

package com.viglet.dumont.connector.plugin.jobs.source.expiry;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.viglet.dumont.connector.plugin.jobs.DumJobsSchema;
import com.viglet.dumont.connector.plugin.jobs.persistence.model.DumJobTombstone;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobOccupationCacheRepository;
import com.viglet.dumont.connector.plugin.jobs.persistence.repository.DumJobTombstoneRepository;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobFetcher;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobHttpFetcher;
import com.viglet.dumont.connector.plugin.jobs.source.http.DumJobRequest;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationEnricher;
import com.viglet.dumont.connector.plugin.jobs.source.location.DumJobLocationResolver;
import com.viglet.dumont.connector.plugin.jobs.source.occupation.DumJobOccupationEnricher;

import lombok.extern.slf4j.Slf4j;

/**
 * Tells the job site which documents left the index (trabalharei <strong>TRA24</strong>), so an old
 * job URL answers "vaga encerrada" with similar openings instead of a bare 404.
 *
 * <p>
 * For every vanished document &mdash; a posting published on its own id, or a {@code job-<hash>}
 * group once its last member expired; never a member that only lost to another source &mdash; the run
 * {@link #queue queues} a row in {@code job_tombstone}, and {@link #flush} POSTs every pending row at
 * the end of the run:
 *
 * <pre>
 * POST {siteUrl}/api/internal/tombstones/
 * Authorization: Bearer &lt;token&gt;
 * {"id":"job-…","title":"…","city":"…","state":"SP","occupation":"…","expiredAt":"2026-10-10T12:00:00Z"}
 * </pre>
 *
 * Absent fields are left out. {@code city}, {@code state} and {@code occupation} are the canonical
 * values the document carried, read through the same location resolver and occupation dictionary
 * (and classifier cache) as the enrichers. A row is deleted once the site answers 2xx; a failure keeps
 * it for the next run and never fails or delays indexing. A document published again before its
 * tombstone went out has the pending row {@link #cancel cancelled}.
 *
 * <p>
 * Settings: {@code dumont.jobs.tombstone.url} (the site, e.g. {@code https://trabalharei.com.br}) and
 * {@code dumont.jobs.tombstone.token}. When either is blank nothing is queued, and each run logs one
 * INFO line saying so. The token is a secret: it travels only in the {@code Authorization} header, is
 * passed to the fetcher as a request secret, and every message logged or stored here is scrubbed of
 * it ({@link DumJobHttpFetcher#scrub}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
@Slf4j
@Component
public class DumJobTombstones {

    /** The site endpoint, appended to {@code dumont.jobs.tombstone.url}. */
    public static final String PATH = "/api/internal/tombstones/";

    /** What one {@link #flush} did. */
    public record Flush(int sent, int failed) {

        public static final Flush NONE = new Flush(0, 0);
    }

    private final String siteUrl;
    private final String token;
    private final DumJobTombstoneRepository repository;
    private final DumJobFetcher fetcher;
    private final DumJobLocationEnricher place;
    private final DumJobOccupationEnricher occupation;
    private final Clock clock;

    @Autowired
    public DumJobTombstones(@Value("${dumont.jobs.tombstone.url:}") String siteUrl,
            @Value("${dumont.jobs.tombstone.token:}") String token, DumJobTombstoneRepository repository,
            DumJobFetcher fetcher, DumJobOccupationCacheRepository occupationCache) {
        this(siteUrl, token, repository, fetcher, DumJobLocationEnricher.standard(),
                DumJobOccupationEnricher.standard(null, occupationCache, null, 0), Clock.systemUTC());
    }

    public DumJobTombstones(String siteUrl, String token, DumJobTombstoneRepository repository,
            DumJobFetcher fetcher, DumJobLocationEnricher place, DumJobOccupationEnricher occupation,
            Clock clock) {
        this.siteUrl = siteUrl == null ? "" : siteUrl.trim();
        this.token = token == null ? "" : token.trim();
        this.repository = repository;
        this.fetcher = fetcher;
        this.place = place;
        this.occupation = occupation;
        this.clock = clock;
    }

    /** Whether both the site URL and the token are set. */
    public boolean enabled() {
        return !siteUrl.isEmpty() && !token.isEmpty();
    }

    /**
     * Queue the tombstone of a document that left the index; a no-op when tombstones are off.
     *
     * @param documentId the id the document was published under
     * @param posting    the adapter posting it was published with (title, place)
     * @param expiredAt  when it expired
     */
    public void queue(String documentId, Map<String, Object> posting, Instant expiredAt) {
        if (!enabled() || documentId == null) {
            return;
        }
        String body = body(documentId, posting, expiredAt);
        DumJobTombstone row = repository.findByDocumentId(documentId)
                .orElseGet(() -> DumJobTombstone.builder().documentId(documentId).build());
        row.setBody(body);
        row.setQueuedAt(clock.instant());
        repository.save(row);
    }

    /** Drop the pending tombstones of documents that were published again. */
    public void cancel(Collection<String> documentIds) {
        if (!enabled() || documentIds.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(documentIds);
        for (int from = 0; from < ids.size(); from += 500) {
            repository.deleteByDocumentIdIn(ids.subList(from, Math.min(ids.size(), from + 500)));
        }
    }

    /**
     * POST every pending tombstone; a success deletes its row, a failure keeps it for the next run.
     * Never throws.
     */
    public Flush flush(String sourceName) {
        if (!enabled()) {
            log.info("Job source '{}': tombstones off — dumont.jobs.tombstone.url or "
                    + "dumont.jobs.tombstone.token is blank", sourceName);
            return Flush.NONE;
        }
        List<DumJobTombstone> pending;
        try {
            pending = repository.findAllByOrderByQueuedAtAsc();
        } catch (RuntimeException e) {
            log.warn("Job tombstones: could not read the pending ones: {}", safe(e));
            return Flush.NONE;
        }
        int sent = 0;
        int failed = 0;
        for (DumJobTombstone row : pending) {
            if (post(row)) {
                sent++;
            } else {
                failed++;
            }
        }
        if (!pending.isEmpty()) {
            log.info("Job source '{}': {} tombstone(s) sent, {} kept for the next run", sourceName, sent,
                    failed);
        }
        return new Flush(sent, failed);
    }

    private boolean post(DumJobTombstone row) {
        try {
            fetcher.send(DumJobRequest.post(endpoint(), Map.of("Authorization", "Bearer " + token),
                    row.getBody(), Set.of(token)));
            repository.delete(row);
            return true;
        } catch (RuntimeException e) {
            String error = safe(e);
            log.warn("Job tombstone for '{}' not sent (attempt {}): {}", row.getDocumentId(),
                    row.getAttempts() + 1, error);
            try {
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(error.length() <= 1024 ? error : error.substring(0, 1024));
                repository.save(row);
            } catch (RuntimeException saveFailure) {
                log.warn("Job tombstone for '{}': could not record the failure: {}", row.getDocumentId(),
                        safe(saveFailure));
            }
            return false;
        }
    }

    private URI endpoint() {
        String base = siteUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + PATH);
    }

    /** An exception as one line, scrubbed of the token. */
    private String safe(RuntimeException e) {
        return DumJobHttpFetcher.scrub(e.getClass().getSimpleName() + ": " + e.getMessage(), Set.of(token));
    }

    /**
     * The JSON body, fields in a fixed order and absent ones left out: {@code id}, {@code title},
     * {@code city}, {@code state}, {@code occupation}, {@code expiredAt} (ISO-8601).
     */
    String body(String documentId, Map<String, Object> posting, Instant expiredAt) {
        String title = text(posting == null ? null : posting.get(DumJobsSchema.TITLE));
        DumJobLocationResolver.Resolution where = posting == null ? null : place.place(posting);
        StringBuilder json = new StringBuilder("{");
        field(json, "id", documentId);
        field(json, "title", title);
        field(json, "city", where == null ? null : where.city());
        field(json, "state", where == null ? null : where.uf());
        field(json, "occupation", title == null ? null : occupation.known(title).orElse(null));
        field(json, "expiredAt", expiredAt == null ? null : expiredAt.toString());
        return json.append('}').toString();
    }

    private static void field(StringBuilder json, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (json.length() > 1) {
            json.append(',');
        }
        json.append(JSONObject.quote(name)).append(':').append(JSONObject.quote(value));
    }

    private static String text(Object value) {
        if (value instanceof Collection<?> values) {
            value = values.isEmpty() ? null : values.iterator().next();
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
