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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.connector.commons.domain.DumIndexInventoryEntry;
import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.persistence.model.DumIndexOrphanReportModel;

import lombok.extern.slf4j.Slf4j;

/**
 * The comparison a content audit could not make (roadmap <strong>D64</strong>): the source and the
 * connector's own ledger against <em>the search index</em>.
 *
 * <p>
 * The scheduled audit answers "did we queue everything the source has". It cannot answer "did
 * everything we queued actually land" — a document the ledger marks processed whose write failed, or
 * one removed from the source whose delete never arrived, satisfies it completely while the index is
 * wrong. That failure is silent, permanent, and until now repairable only by a full reindex.
 *
 * <p>
 * Three differences fall out of enumerating the index:
 * <ul>
 * <li><strong>missing</strong> — in the source, not in the index. Never landed.</li>
 * <li><strong>stale</strong> — in both, but the index's checksum disagrees with what the ledger
 * recorded publishing. Landed, then the write of a later version failed.</li>
 * <li><strong>orphaned</strong> — in the index, not in the source. A ghost whose delete never
 * arrived.</li>
 * </ul>
 *
 * <p>
 * Repair reuses the queue the connector already owns: a missing or stale id gets a NOT_PROCESSED
 * record, exactly as the source-side audit files one, so the existing pipeline reindexes it. Both
 * repairs are additive — the worst case is a document indexed twice.
 *
 * <p>
 * Orphans are <strong>never deleted here</strong>. Deleting index documents on the strength of one
 * discovery pass is the failure the standalone-finish guards (D33, D34) exist to prevent: a source
 * blip that under-reports ids would take live content out of the index. The set is instead handed
 * to {@link DumIndexOrphanService}, which writes it down for an operator to review and apply
 * (roadmap <strong>D68</strong>) — the decision stays deliberate, and the evidence is readable and
 * dated before anything is removed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */
@Slf4j
@Service
public class DumIndexAuditService {

    /** The ledger statuses that mean a document was actually sent to the search engine. */
    private static final Set<DumIndexingStatus> PUBLISHED_STATUSES = Set.of(
            DumIndexingStatus.RECEIVED_AND_SENT_TO_TURING,
            DumIndexingStatus.INDEXED,
            DumIndexingStatus.FINISHED);

    /**
     * What one source's index comparison found. {@code orphanReportId} names the review the
     * orphans were written into (D68), and is {@code null} when the pass found none.
     */
    public record IndexAuditReport(String source, String site, int indexed, int missing, int stale,
            int orphaned, int queued, boolean comparable, Integer orphanReportId) {

        /** A report the backend could not produce — no difference should be acted on. */
        public static IndexAuditReport notComparable(String source, String site) {
            return new IndexAuditReport(source, site, 0, 0, 0, 0, 0, false, null);
        }

        public boolean hasDifferences() {
            return missing > 0 || stale > 0 || orphaned > 0;
        }
    }

    private final DumConnectorIndexingService indexingService;
    private final DumIndexingPlugin indexingPlugin;
    private final DumIndexOrphanService orphanService;
    private final boolean enabled;

    public DumIndexAuditService(DumConnectorIndexingService indexingService,
            DumIndexingPlugin indexingPlugin, DumIndexOrphanService orphanService,
            @Value("${dumont.audit.index.enabled:true}") boolean enabled) {
        this.indexingService = indexingService;
        this.indexingPlugin = indexingPlugin;
        this.orphanService = orphanService;
        this.enabled = enabled;
    }

    /**
     * Compares one source against the index on each of its sites and queues the repairs.
     *
     * @param source        the source name
     * @param provider      the connector provider, for the ledger lookups
     * @param sites         the sites this source publishes to
     * @param discoveredIds every id the source reported in this audit pass
     * @param locale        the locale to stamp on any record filed for repair
     * @return one report per site; empty when the comparison is switched off or there is nothing to
     *         compare against
     */
    public List<IndexAuditReport> audit(String source, String provider, Collection<String> sites,
            Collection<String> discoveredIds, Locale locale) {
        if (!enabled || sites.isEmpty()) {
            return List.of();
        }
        if (discoveredIds.isEmpty()) {
            // A pass that discovered nothing cannot tell a truly empty source from a failed
            // discovery, and every indexed document would read as orphaned (D09's safe-run rule).
            log.warn("Index audit skipped for source '{}': discovery reported no ids", source);
            return List.of();
        }
        Set<String> sourceIds = new LinkedHashSet<>(discoveredIds);
        Map<String, String> ledgerChecksums = ledgerChecksums(source, provider);
        return sites.stream()
                .map(site -> auditSite(source, provider, site, sourceIds, ledgerChecksums, locale))
                .toList();
    }

    private IndexAuditReport auditSite(String source, String provider, String site,
            Set<String> sourceIds, Map<String, String> ledgerChecksums, Locale locale) {
        Optional<List<DumIndexInventoryEntry>> inventory = indexingPlugin.inventory(site, source);
        if (inventory.isEmpty()) {
            log.warn("Index audit for source '{}' site '{}': the backend cannot report its "
                    + "inventory — no difference acted on", source, site);
            return IndexAuditReport.notComparable(source, site);
        }
        List<DumIndexInventoryEntry> indexed = inventory.get();
        Map<String, DumIndexInventoryEntry> byId = new LinkedHashMap<>();
        indexed.forEach(entry -> byId.put(entry.id(), entry));

        List<String> missing = sourceIds.stream().filter(id -> !byId.containsKey(id)).toList();
        List<String> stale = sourceIds.stream()
                .filter(byId::containsKey)
                .filter(id -> byId.get(id).differsFrom(ledgerChecksums.get(id)))
                .toList();
        List<String> orphaned = byId.keySet().stream().filter(id -> !sourceIds.contains(id)).toList();

        int queued = queueForRepair(source, provider, site, locale, missing, stale);
        // D68: the ghosts go to a review, not to a delete — and an agreeing pass retracts whatever
        // the previous one recorded, so a report only ever describes the latest evidence.
        Integer orphanReportId = orphanService
                .record(source, provider, site, locale, orphaned)
                .map(DumIndexOrphanReportModel::getId)
                .orElse(null);
        report(source, site, indexed.size(), missing, stale, orphaned, queued, orphanReportId);
        return new IndexAuditReport(source, site, indexed.size(), missing.size(), stale.size(),
                orphaned.size(), queued, true, orphanReportId);
    }

    /**
     * Files a NOT_PROCESSED record for each id the index is missing or holds stale, unless the
     * ledger already carries one — the queue is idempotent, and a second record would only double
     * the work.
     */
    private int queueForRepair(String source, String provider, String site, Locale locale,
            List<String> missing, List<String> stale) {
        int queued = 0;
        for (String objectId : concat(missing, stale)) {
            if (indexingService.existsUnprocessedByObjectIdAndSourceAndProvider(objectId, source,
                    provider)) {
                continue;
            }
            indexingService.createUnprocessedRecord(objectId, source, provider, locale, null,
                    List.of(site));
            queued++;
        }
        return queued;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).distinct().toList();
    }

    private void report(String source, String site, int indexed, List<String> missing,
            List<String> stale, List<String> orphaned, int queued, Integer orphanReportId) {
        if (missing.isEmpty() && stale.isEmpty() && orphaned.isEmpty()) {
            log.info("Index audit for source '{}' site '{}': {} document(s), index agrees with "
                    + "the source", source, site, indexed);
            return;
        }
        log.warn("Index audit for source '{}' site '{}': {} indexed, {} missing, {} stale, "
                + "{} orphaned; {} queued for reindex", source, site, indexed, missing.size(),
                stale.size(), orphaned.size(), queued);
        logIds("missing from the index", source, missing);
        logIds("stale in the index", source, stale);
        logIds("orphaned in the index", source, orphaned);
        if (!orphaned.isEmpty() && orphanReportId != null) {
            log.warn("Index audit for source '{}': {} orphaned document(s) left in place and "
                    + "recorded as orphan report {} — review it under "
                    + "/api/v2/connector/orphans/{} and apply it once you have confirmed the ids "
                    + "are gone from the source", source, orphaned.size(), orphanReportId,
                    orphanReportId);
        }
    }

    private static void logIds(String what, String source, List<String> ids) {
        if (!ids.isEmpty()) {
            log.warn("Index audit for source '{}' — {}: {}", source, what, ids);
        }
    }

    /**
     * What the ledger recorded actually <em>publishing</em> per id, so "stale" compares like with
     * like.
     *
     * <p>
     * Only the statuses that mean a document was sent count. A {@code PREPARE_*} or
     * {@code SENT_TO_QUEUE} row carries the checksum the connector <em>intends</em> to publish, which
     * legitimately differs from what the index holds until the write completes — reading those as
     * published would report every in-flight document as stale on every audit.
     */
    private Map<String, String> ledgerChecksums(String source, String provider) {
        Map<String, String> checksums = new LinkedHashMap<>();
        for (DumConnectorIndexingModel record : indexingService.getAllBySourceAndProvider(source,
                provider)) {
            if (!PUBLISHED_STATUSES.contains(record.getStatus()) || record.getObjectId() == null
                    || record.getChecksum() == null) {
                continue;
            }
            checksums.putIfAbsent(record.getObjectId(), record.getChecksum());
        }
        return checksums;
    }
}
