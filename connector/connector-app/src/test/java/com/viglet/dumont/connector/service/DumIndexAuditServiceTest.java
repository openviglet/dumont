/*
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.connector.commons.domain.DumIndexInventoryEntry;
import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.persistence.model.DumIndexOrphanReportModel;
import com.viglet.dumont.connector.service.DumIndexAuditService.IndexAuditReport;

/** The audit's third comparison: source and ledger against the index (roadmap D64). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DumIndexAuditService Tests (D64)")
class DumIndexAuditServiceTest {

    private static final String SOURCE = "wknd";
    private static final String PROVIDER = "AEM";
    private static final String SITE = "Sample";

    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private DumIndexingPlugin indexingPlugin;
    @Mock
    private DumIndexOrphanService orphanService;

    private DumIndexAuditService service;

    @BeforeEach
    void setUp() {
        service = new DumIndexAuditService(indexingService, indexingPlugin, orphanService, true);
        when(indexingService.getAllBySourceAndProvider(SOURCE, PROVIDER)).thenReturn(List.of());
        when(indexingService.existsUnprocessedByObjectIdAndSourceAndProvider(anyString(),
                anyString(), anyString())).thenReturn(false);
        when(orphanService.record(anyString(), anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(Optional.empty());
    }

    private void indexHolds(DumIndexInventoryEntry... entries) {
        when(indexingPlugin.inventory(SITE, SOURCE)).thenReturn(Optional.of(List.of(entries)));
    }

    private static DumConnectorIndexingModel ledger(String objectId, String checksum) {
        return DumConnectorIndexingModel.builder()
                .objectId(objectId)
                .source(SOURCE)
                .provider(PROVIDER)
                .checksum(checksum)
                .status(DumIndexingStatus.INDEXED)
                .build();
    }

    private IndexAuditReport auditOf(List<String> sourceIds) {
        List<IndexAuditReport> reports = service.audit(SOURCE, PROVIDER, Set.of(SITE), sourceIds,
                Locale.US);
        assertThat(reports).hasSize(1);
        return reports.getFirst();
    }

    @Test
    @DisplayName("an id the source has and the index does not is missing, and is queued")
    void aDocumentThatNeverLandedIsMissingAndQueued() {
        indexHolds(new DumIndexInventoryEntry("/a", 1L, "c1"));

        IndexAuditReport report = auditOf(List.of("/a", "/b"));

        assertThat(report.missing()).isEqualTo(1);
        assertThat(report.orphaned()).isZero();
        assertThat(report.queued()).isEqualTo(1);
        verify(indexingService).createUnprocessedRecord(eq("/b"), eq(SOURCE), eq(PROVIDER),
                eq(Locale.US), eq(null), eq(List.of(SITE)));
    }

    @Test
    @DisplayName("an id the index has and the source does not is orphaned, and is never deleted")
    void aGhostIsReportedButNeverDeleted() {
        indexHolds(new DumIndexInventoryEntry("/a", 1L, "c1"),
                new DumIndexInventoryEntry("/gone", 1L, "c9"));

        IndexAuditReport report = auditOf(List.of("/a"));

        assertThat(report.orphaned()).isEqualTo(1);
        assertThat(report.missing()).isZero();
        // Nothing is queued for a ghost: the repair for one is a deliberate deindex, not a reindex.
        assertThat(report.queued()).isZero();
        verify(indexingService, never()).createUnprocessedRecord(anyString(), anyString(),
                anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("the orphan set goes to the review, and the audit reports which one (D68)")
    void aGhostIsHandedToTheReview() {
        indexHolds(new DumIndexInventoryEntry("/a", 1L, "c1"),
                new DumIndexInventoryEntry("/gone", 1L, "c9"));
        when(orphanService.record(SOURCE, PROVIDER, SITE, Locale.US, List.of("/gone"), 2))
                .thenReturn(Optional.of(new DumIndexOrphanReportModel().setId(7)));

        assertThat(auditOf(List.of("/a")).orphanReportId()).isEqualTo(7);
        // The indexed count travels with the set: it is what tells a reader whether a report is a
        // cleanup or a discovery pass that under-reported.
        verify(orphanService).record(SOURCE, PROVIDER, SITE, Locale.US, List.of("/gone"), 2);
    }

    @Test
    @DisplayName("an agreeing pass still calls the review, so a stale report is retracted (D68)")
    void anAgreeingPassRetractsAStandingReport() {
        indexHolds(new DumIndexInventoryEntry("/a", 1L, "c1"));

        assertThat(auditOf(List.of("/a")).orphanReportId()).isNull();
        // Empty, not skipped: the service reads "no orphans" as a retraction of what it holds.
        verify(orphanService).record(SOURCE, PROVIDER, SITE, Locale.US, List.of(), 1);
    }

    @Test
    @DisplayName("a checksum disagreeing with the ledger is stale, and is queued")
    void aDocumentWhoseLaterWriteFailedIsStaleAndQueued() {
        indexHolds(new DumIndexInventoryEntry("/a", 1L, "old-checksum"));
        when(indexingService.getAllBySourceAndProvider(SOURCE, PROVIDER))
                .thenReturn(List.of(ledger("/a", "new-checksum")));

        IndexAuditReport report = auditOf(List.of("/a"));

        assertThat(report.stale()).isEqualTo(1);
        assertThat(report.queued()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown checksum on either side is not a difference")
    void unknownIsNotDifferent() {
        // The index has no checksum for the document, and the ledger does.
        indexHolds(new DumIndexInventoryEntry("/a", 1L, null));
        when(indexingService.getAllBySourceAndProvider(SOURCE, PROVIDER))
                .thenReturn(List.of(ledger("/a", "c1")));
        assertThat(auditOf(List.of("/a")).stale()).isZero();

        // And the other way round: the ledger recorded no checksum.
        indexHolds(new DumIndexInventoryEntry("/a", 1L, "c1"));
        when(indexingService.getAllBySourceAndProvider(SOURCE, PROVIDER))
                .thenReturn(List.of(ledger("/a", null)));
        assertThat(auditOf(List.of("/a")).stale()).isZero();
    }

    @Test
    @DisplayName("a backend that cannot report its inventory yields no acted-on difference")
    void anUnreadableIndexIsNotAnEmptyIndex() {
        when(indexingPlugin.inventory(SITE, SOURCE)).thenReturn(Optional.empty());

        IndexAuditReport report = auditOf(List.of("/a", "/b"));

        assertThat(report.comparable()).isFalse();
        assertThat(report.missing()).isZero();
        verify(indexingService, never()).createUnprocessedRecord(anyString(), anyString(),
                anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("a discovery pass that found nothing is skipped, not read as a wholly stale index")
    void anEmptyDiscoveryIsSkipped() {
        indexHolds(new DumIndexInventoryEntry("/a", 1L, "c1"));

        assertThat(service.audit(SOURCE, PROVIDER, Set.of(SITE), List.of(), Locale.US)).isEmpty();
        verify(indexingPlugin, never()).inventory(anyString(), anyString());
    }

    @Test
    @DisplayName("a repair already queued is not queued twice")
    void anAlreadyQueuedRepairIsNotDuplicated() {
        indexHolds(new DumIndexInventoryEntry("/a", 1L, "c1"));
        when(indexingService.existsUnprocessedByObjectIdAndSourceAndProvider("/b", SOURCE,
                PROVIDER)).thenReturn(true);

        IndexAuditReport report = auditOf(List.of("/a", "/b"));

        assertThat(report.missing()).isEqualTo(1);
        assertThat(report.queued()).isZero();
        verify(indexingService, never()).createUnprocessedRecord(anyString(), anyString(),
                anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("an id both missing and stale is queued once")
    void missingAndStaleDoNotQueueTheSameIdTwice() {
        indexHolds(new DumIndexInventoryEntry("/a", 1L, "old"));
        when(indexingService.getAllBySourceAndProvider(SOURCE, PROVIDER))
                .thenReturn(List.of(ledger("/a", "new")));

        IndexAuditReport report = auditOf(List.of("/a", "/b"));

        assertThat(report.missing()).isEqualTo(1);
        assertThat(report.stale()).isEqualTo(1);
        assertThat(report.queued()).isEqualTo(2);
    }

    @Test
    @DisplayName("an agreeing index reports no difference and queues nothing")
    void anAgreeingIndexIsClean() {
        indexHolds(new DumIndexInventoryEntry("/a", 1L, "c1"));
        when(indexingService.getAllBySourceAndProvider(SOURCE, PROVIDER))
                .thenReturn(List.of(ledger("/a", "c1")));

        IndexAuditReport report = auditOf(List.of("/a"));

        assertThat(report.hasDifferences()).isFalse();
        assertThat(report.comparable()).isTrue();
        assertThat(report.indexed()).isEqualTo(1);
    }

    @Test
    @DisplayName("switched off, or with no site, the comparison does not run")
    void disabledOrSitelessDoesNothing() {
        DumIndexAuditService off = new DumIndexAuditService(indexingService, indexingPlugin,
                orphanService, false);
        assertThat(off.audit(SOURCE, PROVIDER, Set.of(SITE), List.of("/a"), Locale.US)).isEmpty();
        assertThat(service.audit(SOURCE, PROVIDER, Set.of(), List.of("/a"), Locale.US)).isEmpty();
        verify(indexingPlugin, never()).inventory(anyString(), anyString());
    }

    @Test
    @DisplayName("an in-flight ledger row is not a published checksum")
    void anInFlightLedgerRowIsNotAPublishedChecksum() {
        // A row whose write has not completed carries the checksum the connector *intends* to
        // publish; reading it as published would report every in-flight document as stale.
        for (DumIndexingStatus inFlight : List.of(DumIndexingStatus.NOT_PROCESSED,
                DumIndexingStatus.SENT_TO_QUEUE, DumIndexingStatus.PREPARE_REINDEX)) {
            indexHolds(new DumIndexInventoryEntry("/a", 1L, "in-the-index"));
            DumConnectorIndexingModel pending = ledger("/a", "not-sent-yet");
            pending.setStatus(inFlight);
            when(indexingService.getAllBySourceAndProvider(SOURCE, PROVIDER))
                    .thenReturn(List.of(pending));

            assertThat(auditOf(List.of("/a")).stale())
                    .as("status %s must not count as published", inFlight)
                    .isZero();
        }
    }
}
