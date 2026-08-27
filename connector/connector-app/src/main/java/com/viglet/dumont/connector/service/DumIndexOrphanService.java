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

package com.viglet.dumont.connector.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.persistence.model.DumIndexOrphanReportModel;
import com.viglet.dumont.connector.persistence.model.DumIndexOrphanReportModel.ReportStatus;
import com.viglet.dumont.connector.persistence.repository.DumIndexOrphanReportRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * The deliberate step between naming a ghost and removing it (roadmap <strong>D68</strong>).
 *
 * <p>
 * D64's index audit reports three differences and repairs two. The third — a document still in the
 * index after leaving the source — is named and left alone, because an orphan is only an orphan if
 * the discovery pass that failed to mention it was complete. A flaky gateway or a listing that
 * paginated short makes every unlisted id look like a ghost, and deleting on that evidence is the
 * failure D33 and D34 exist to prevent, performed against a lie.
 *
 * <p>
 * So the audit writes the set down instead of acting on it, and an operator decides. That shape is
 * what makes deletion safe: the evidence is dated and readable before anything is removed, and a
 * report produced by an unreachable source is one an operator recognises and discards.
 *
 * <p>
 * Two constraints hold the design together:
 * <ul>
 * <li><strong>The apply path reuses the connector's own deindex</strong>
 * ({@link DumConnectorContext#deIndexById}) rather than deleting behind its back, and drops the
 * ledger rows for the same ids — otherwise the ledger would keep describing documents the index no
 * longer holds.</li>
 * <li><strong>A report expires.</strong> Applying week-old evidence to a source reindexed since
 * would delete live content, so an aged report is refused and must be re-established by a fresh
 * audit.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
@Slf4j
@Service
public class DumIndexOrphanService {

    /** What happened to a report an operator acted on. */
    public enum Outcome {
        /** The ids were queued for deindex and the report closed. */
        APPLIED,
        /** The report was closed without deleting anything. */
        DISCARDED,
        /** No report carries that id. */
        NOT_FOUND,
        /** The report was already applied or discarded — a decision is made once. */
        NOT_PENDING,
        /** The evidence aged out; a fresh audit must re-establish it. */
        EXPIRED
    }

    /** The result of applying a report: what happened, and how many ids it deindexed. */
    public record ApplyResult(Outcome outcome, int deindexed) {

        public static ApplyResult of(Outcome outcome) {
            return new ApplyResult(outcome, 0);
        }
    }

    /**
     * A report as an operator reads it — the stored row plus the two things only the clock can say:
     * when the evidence stops being usable, and whether it already has.
     */
    public record OrphanReport(int id, String source, String provider, String site,
            Instant discoveredAt, Instant expiresAt, boolean expired, ReportStatus status,
            Instant decidedAt, int count, int indexedCount, List<String> objectIds) {
    }

    private final DumIndexOrphanReportRepository repository;
    private final DumConnectorIndexingService indexingService;
    private final DumConnectorContext connectorContext;
    private final Duration ttl;

    public DumIndexOrphanService(DumIndexOrphanReportRepository repository,
            DumConnectorIndexingService indexingService, DumConnectorContext connectorContext,
            @Value("${dumont.audit.index.orphanTtl:PT24H}") Duration ttl) {
        this.repository = repository;
        this.indexingService = indexingService;
        this.connectorContext = connectorContext;
        this.ttl = ttl;
    }

    /**
     * Records what one audit pass found orphaned on one site, superseding whatever the previous
     * pass recorded for the same source and site.
     *
     * <p>
     * Superseding is a delete, not an update: the older row's id stops resolving, so an operator
     * who reviewed a set two passes ago cannot apply it by id after the evidence moved on. An
     * agreeing pass — no orphans — retracts the pending report instead of leaving it standing.
     *
     * @return the recorded report, or empty when there was nothing to record
     */
    public Optional<DumIndexOrphanReportModel> record(String source, String provider, String site,
            Locale locale, Collection<String> orphanIds, int indexedCount) {
        List<DumIndexOrphanReportModel> superseded = repository
                .findAllByProviderAndSourceAndSiteAndStatus(provider, source, site,
                        ReportStatus.PENDING);
        if (!superseded.isEmpty()) {
            repository.deleteAll(superseded);
        }
        if (orphanIds == null || orphanIds.isEmpty()) {
            if (!superseded.isEmpty()) {
                log.info("Index audit for source '{}' site '{}': the index no longer holds any "
                        + "orphan — {} pending report(s) retracted", source, site,
                        superseded.size());
            }
            return Optional.empty();
        }
        DumIndexOrphanReportModel report = repository.save(DumIndexOrphanReportModel.builder()
                .source(source)
                .provider(provider)
                .site(site)
                .locale(locale)
                .discoveredAt(Instant.now())
                .indexedCount(indexedCount)
                .status(ReportStatus.PENDING)
                .objectIds(new ArrayList<>(orphanIds))
                .build());
        log.warn("Index audit for source '{}' site '{}': {} orphan(s) recorded as report {} — "
                + "review it and apply or discard it; nothing is deleted until you do", source,
                site, report.getObjectIds().size(), report.getId());
        return Optional.of(report);
    }

    /** Every report, newest evidence first; one source's when {@code source} is given. */
    public List<OrphanReport> reports(String source) {
        List<DumIndexOrphanReportModel> rows = source == null || source.isBlank()
                ? repository.findAllByOrderByDiscoveredAtDescIdDesc()
                : repository.findAllBySourceOrderByDiscoveredAtDescIdDesc(source);
        return rows.stream().map(this::view).toList();
    }

    public Optional<OrphanReport> report(int id) {
        return repository.findById(id).map(this::view);
    }

    /**
     * Deindexes a pending report's ids through the connector's own delete path and closes it.
     *
     * <p>
     * The ledger rows for the same ids go too. An orphan is an id the source no longer has, so a
     * ledger row claiming it was published describes a document that is about to stop existing;
     * leaving it would make the next audit read the id as missing and requeue what was just
     * removed.
     */
    public ApplyResult apply(int id) {
        Optional<DumIndexOrphanReportModel> found = repository.findById(id);
        if (found.isEmpty()) {
            return ApplyResult.of(Outcome.NOT_FOUND);
        }
        DumIndexOrphanReportModel report = found.get();
        if (report.getStatus() != ReportStatus.PENDING) {
            return ApplyResult.of(Outcome.NOT_PENDING);
        }
        if (isExpired(report)) {
            log.warn("Orphan report {} for source '{}' site '{}' expired at {} — refusing to apply "
                    + "evidence a reindex may have overtaken; re-run the audit", id,
                    report.getSource(), report.getSite(), expiresAt(report));
            return ApplyResult.of(Outcome.EXPIRED);
        }
        DumConnectorSession session = new DumConnectorSession(report.getSource(),
                report.getProvider(), report.getLocale());
        int deindexed = connectorContext.deIndexById(session, report.getObjectIds(),
                List.of(report.getSite()));
        indexingService.deleteByProviderAndSourceAndObjectIdIn(report.getProvider(),
                report.getSource(), report.getObjectIds());
        close(report, ReportStatus.APPLIED);
        log.warn("Orphan report {} applied: {} document(s) deindexed from source '{}' site '{}'",
                id, deindexed, report.getSource(), report.getSite());
        return new ApplyResult(Outcome.APPLIED, deindexed);
    }

    /** Closes a pending report without deleting anything — the operator rejected the evidence. */
    public Outcome discard(int id) {
        Optional<DumIndexOrphanReportModel> found = repository.findById(id);
        if (found.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        DumIndexOrphanReportModel report = found.get();
        if (report.getStatus() != ReportStatus.PENDING) {
            return Outcome.NOT_PENDING;
        }
        close(report, ReportStatus.DISCARDED);
        log.info("Orphan report {} discarded for source '{}' site '{}' — nothing was deleted", id,
                report.getSource(), report.getSite());
        return Outcome.DISCARDED;
    }

    private void close(DumIndexOrphanReportModel report, ReportStatus status) {
        report.setStatus(status);
        report.setDecidedAt(Instant.now());
        repository.save(report);
    }

    private OrphanReport view(DumIndexOrphanReportModel report) {
        return new OrphanReport(report.getId(), report.getSource(), report.getProvider(),
                report.getSite(), report.getDiscoveredAt(), expiresAt(report), isExpired(report),
                report.getStatus(), report.getDecidedAt(), report.getObjectIds().size(),
                report.getIndexedCount(), report.getObjectIds());
    }

    /** When the evidence stops being usable; {@code null} when expiry is switched off. */
    private Instant expiresAt(DumIndexOrphanReportModel report) {
        if (neverExpires() || report.getDiscoveredAt() == null) {
            return null;
        }
        return report.getDiscoveredAt().plus(ttl);
    }

    private boolean isExpired(DumIndexOrphanReportModel report) {
        Instant expiresAt = expiresAt(report);
        return expiresAt != null && !Instant.now().isBefore(expiresAt);
    }

    /** {@code dumont.audit.index.orphanTtl} at or below zero keeps a report usable indefinitely. */
    private boolean neverExpires() {
        return ttl == null || ttl.isZero() || ttl.isNegative();
    }
}
