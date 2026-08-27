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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.dumont.connector.commons.DumConnectorContext;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.persistence.model.DumIndexOrphanReportModel;
import com.viglet.dumont.connector.persistence.model.DumIndexOrphanReportModel.ReportStatus;
import com.viglet.dumont.connector.persistence.repository.DumIndexOrphanReportRepository;
import com.viglet.dumont.connector.service.DumIndexOrphanService.ApplyResult;
import com.viglet.dumont.connector.service.DumIndexOrphanService.Outcome;

/** The deliberate step between naming a ghost and removing it (roadmap D68). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DumIndexOrphanService Tests (D68)")
class DumIndexOrphanServiceTest {

    private static final String SOURCE = "wknd";
    private static final String PROVIDER = "AEM";
    private static final String SITE = "Sample";
    private static final Duration ONE_DAY = Duration.ofHours(24);

    @Mock
    private DumIndexOrphanReportRepository repository;
    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private DumConnectorContext connectorContext;

    private DumIndexOrphanService service;

    @BeforeEach
    void setUp() {
        service = new DumIndexOrphanService(repository, indexingService, connectorContext, ONE_DAY);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(repository.findAllByProviderAndSourceAndSiteAndStatus(anyString(), anyString(),
                anyString(), any())).thenReturn(List.of());
        when(connectorContext.deIndexById(any(), any(), any())).thenReturn(2);
    }

    private static DumIndexOrphanReportModel pending(int id, String... ids) {
        return DumIndexOrphanReportModel.builder()
                .id(id)
                .source(SOURCE)
                .provider(PROVIDER)
                .site(SITE)
                .locale(Locale.US)
                .discoveredAt(Instant.now())
                .status(ReportStatus.PENDING)
                .objectIds(new ArrayList<>(List.of(ids)))
                .build();
    }

    private void repositoryHolds(DumIndexOrphanReportModel report) {
        when(repository.findById(report.getId())).thenReturn(Optional.of(report));
    }

    @Test
    @DisplayName("an audit's orphans are written down as a pending report, not deleted")
    void orphansAreRecordedPending() {
        Optional<DumIndexOrphanReportModel> saved = service.record(SOURCE, PROVIDER, SITE,
                Locale.US, List.of("/gone", "/also-gone"));

        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(saved.get().getObjectIds()).containsExactly("/gone", "/also-gone");
        verify(connectorContext, never()).deIndexById(any(), any(), any());
    }

    @Test
    @DisplayName("a fresh pass supersedes the previous report rather than adding a second")
    void afreshPassSupersedesTheOlderEvidence() {
        DumIndexOrphanReportModel older = pending(1, "/stale-evidence");
        when(repository.findAllByProviderAndSourceAndSiteAndStatus(PROVIDER, SOURCE, SITE,
                ReportStatus.PENDING)).thenReturn(List.of(older));

        service.record(SOURCE, PROVIDER, SITE, Locale.US, List.of("/gone"));

        // Deleted, not updated: an id that stops resolving is what stops an operator applying a
        // set they reviewed two passes ago.
        verify(repository).deleteAll(List.of(older));
    }

    @Test
    @DisplayName("a pass that finds no orphan retracts the report the last one left standing")
    void anAgreeingPassRetractsTheStandingReport() {
        DumIndexOrphanReportModel older = pending(1, "/was-a-ghost");
        when(repository.findAllByProviderAndSourceAndSiteAndStatus(PROVIDER, SOURCE, SITE,
                ReportStatus.PENDING)).thenReturn(List.of(older));

        assertThat(service.record(SOURCE, PROVIDER, SITE, Locale.US, List.of())).isEmpty();

        verify(repository).deleteAll(List.of(older));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("applying a report deindexes through the connector's own delete path")
    void applyingReusesTheConnectorsDeindex() {
        DumIndexOrphanReportModel report = pending(5, "/gone", "/also-gone");
        repositoryHolds(report);

        ApplyResult result = service.apply(5);

        assertThat(result.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(result.deindexed()).isEqualTo(2);

        ArgumentCaptor<DumConnectorSession> session =
                ArgumentCaptor.forClass(DumConnectorSession.class);
        verify(connectorContext).deIndexById(session.capture(),
                any(), any());
        assertThat(session.getValue().getSource()).isEqualTo(SOURCE);
        assertThat(session.getValue().getProviderName()).isEqualTo(PROVIDER);
        assertThat(session.getValue().getLocale()).isEqualTo(Locale.US);

        // The ledger goes with it: a row claiming a deleted document was published would make the
        // next audit read the id as missing and requeue what was just removed.
        verify(indexingService).deleteByProviderAndSourceAndObjectIdIn(PROVIDER, SOURCE,
                List.of("/gone", "/also-gone"));
        assertThat(report.getStatus()).isEqualTo(ReportStatus.APPLIED);
        assertThat(report.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("evidence older than the TTL is refused, and nothing is deleted")
    void agedEvidenceIsRefused() {
        DumIndexOrphanReportModel report = pending(5, "/gone");
        report.setDiscoveredAt(Instant.now().minus(Duration.ofDays(7)));
        repositoryHolds(report);

        assertThat(service.apply(5).outcome()).isEqualTo(Outcome.EXPIRED);

        verify(connectorContext, never()).deIndexById(any(), any(), any());
        verify(indexingService, never()).deleteByProviderAndSourceAndObjectIdIn(anyString(),
                anyString(), any());
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("expiry switched off keeps old evidence applicable")
    void expirySwitchedOffKeepsEvidenceApplicable() {
        DumIndexOrphanService noExpiry = new DumIndexOrphanService(repository, indexingService,
                connectorContext, Duration.ZERO);
        DumIndexOrphanReportModel report = pending(5, "/gone");
        report.setDiscoveredAt(Instant.now().minus(Duration.ofDays(7)));
        repositoryHolds(report);

        assertThat(noExpiry.apply(5).outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(noExpiry.report(5)).get().extracting(DumIndexOrphanService.OrphanReport::expired)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("a decision is made once — a closed report is not applied again")
    void aClosedReportIsNotAppliedTwice() {
        DumIndexOrphanReportModel report = pending(5, "/gone");
        report.setStatus(ReportStatus.APPLIED);
        repositoryHolds(report);

        assertThat(service.apply(5).outcome()).isEqualTo(Outcome.NOT_PENDING);
        assertThat(service.discard(5)).isEqualTo(Outcome.NOT_PENDING);
        verify(connectorContext, never()).deIndexById(any(), any(), any());
    }

    @Test
    @DisplayName("an id no report carries is not found")
    void anUnknownReportIsNotFound() {
        when(repository.findById(99)).thenReturn(Optional.empty());

        assertThat(service.apply(99).outcome()).isEqualTo(Outcome.NOT_FOUND);
        assertThat(service.discard(99)).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    @DisplayName("discarding closes the report and deletes nothing")
    void discardingDeletesNothing() {
        DumIndexOrphanReportModel report = pending(5, "/gone");
        repositoryHolds(report);

        assertThat(service.discard(5)).isEqualTo(Outcome.DISCARDED);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.DISCARDED);
        assertThat(report.getDecidedAt()).isNotNull();
        verify(connectorContext, never()).deIndexById(any(), any(), any());
        verify(indexingService, never()).deleteByProviderAndSourceAndObjectIdIn(anyString(),
                anyString(), any());
    }

    @Test
    @DisplayName("a report reads back with when its evidence stops being usable")
    void aReportCarriesItsExpiry() {
        DumIndexOrphanReportModel report = pending(5, "/gone");
        repositoryHolds(report);

        DumIndexOrphanService.OrphanReport view = service.report(5).orElseThrow();

        assertThat(view.expired()).isFalse();
        assertThat(view.count()).isEqualTo(1);
        assertThat(view.expiresAt()).isEqualTo(report.getDiscoveredAt().plus(ONE_DAY));
    }
}
