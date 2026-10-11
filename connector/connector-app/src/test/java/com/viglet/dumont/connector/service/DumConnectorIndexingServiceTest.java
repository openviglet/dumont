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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.domain.DumConnectorMonitoringPage;
import com.viglet.dumont.connector.domain.DumConnectorMonitoringRequest;
import com.viglet.dumont.connector.domain.DumSNSiteLocale;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingStatsModel.OperationType;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingRepository;
import com.viglet.dumont.connector.persistence.repository.DumConnectorIndexingStatsRepository;
import com.viglet.turing.client.sn.job.TurSNJobItem;

@ExtendWith(MockitoExtension.class)
class DumConnectorIndexingServiceTest {

    @Mock
    private DumConnectorIndexingRepository repository;
    @Mock
    private DumConnectorIndexingStatsRepository statsRepository;
    @Mock
    private DumRunWarningNotifier notifier;

    private DumConnectorIndexingService service;

    @BeforeEach
    void setUp() {
        service = new DumConnectorIndexingService(repository, statsRepository, true, notifier);
    }

    private static DumJobItemWithSession jobItem() {
        TurSNJobItem item = org.mockito.Mockito.mock(TurSNJobItem.class);
        org.mockito.Mockito.lenient().when(item.getId()).thenReturn("obj-1");
        org.mockito.Mockito.lenient().when(item.getEnvironment()).thenReturn("prod");
        DumConnectorSession session = new DumConnectorSession("source-1", "turing", Locale.US);
        return new DumJobItemWithSession(item, session, Set.of("dep-1"), false);
    }

    @Test
    void processingFlagsAreTracked() {
        assertThat(service.tryStartProcessing("s")).isTrue();
        assertThat(service.tryStartProcessing("s")).isFalse();
        assertThat(service.isProcessing("s")).isTrue();
        service.finishProcessing("s");
        assertThat(service.isProcessing("s")).isFalse();
    }

    @Test
    void completeIndexingStatsPersistsComputedStats() {
        service.trackIndexingStart("source-1", "turing", OperationType.INDEX_ALL);
        when(repository.countBySourceAndProviderAndModificationDateGreaterThanEqual(
                eq("source-1"), eq("turing"), any(Instant.class))).thenReturn(10L);
        when(repository.findAllEnvironmentsBySourceAndProvider("source-1", "turing"))
                .thenReturn(List.of("prod"));
        when(repository.findAllLocalesBySourceAndProvider("source-1", "turing"))
                .thenReturn(List.of(Locale.US));
        when(repository.distinctSites("source-1", "turing")).thenReturn(List.of("site1"));

        service.completeIndexingStats("source-1", "turing");

        verify(statsRepository).save(any(DumConnectorIndexingStatsModel.class));
    }

    @Test
    void aRecordedOutcomeIsWrittenOnTheRunsStatsRow() {
        // D96: a budget stop shows in the run record, not only in the logs.
        service.trackIndexingStart("source-1", "turing", OperationType.INDEX_ALL);
        service.recordRunOutcome("source-1", "turing", true,
                List.of("stopped: the day allowance of 250 calls is spent"));
        assertThat(service.hasRunOutcome("source-1", "turing")).isTrue();

        service.completeIndexingStats("source-1", "turing");

        org.mockito.ArgumentCaptor<DumConnectorIndexingStatsModel> saved =
                org.mockito.ArgumentCaptor.forClass(DumConnectorIndexingStatsModel.class);
        verify(statsRepository).save(saved.capture());
        assertThat(saved.getValue().isPartial()).isTrue();
        assertThat(saved.getValue().getNotes())
                .containsExactly("stopped: the day allowance of 250 calls is spent");
        assertThat(service.hasRunOutcome("source-1", "turing")).isFalse();
    }

    @Test
    void aCompleteRunsWarningMarksItsStatsRowWarnedButNotPartial() {
        // D130: a partner that quietly stopped returning items is not a healthy run.
        service.trackIndexingStart("source-1", "turing", OperationType.INDEX_ALL);
        service.recordRunOutcome("source-1", "turing", false, List.of("12 records"),
                List.of("new postings fell 92% under the 7-day median"));

        service.completeIndexingStats("source-1", "turing");

        org.mockito.ArgumentCaptor<DumConnectorIndexingStatsModel> saved =
                org.mockito.ArgumentCaptor.forClass(DumConnectorIndexingStatsModel.class);
        verify(statsRepository).save(saved.capture());
        assertThat(saved.getValue().isPartial()).isFalse();
        assertThat(saved.getValue().isWarned()).isTrue();
        assertThat(saved.getValue().getWarnings())
                .containsExactly("new postings fell 92% under the 7-day median");
        assertThat(saved.getValue().getNotes()).containsExactly("12 records");
    }

    @Test
    void theNotifierHearsWhenASourceStartsWarningButNotWhileItStaysSo() {
        // D130: one message per incident, not one per run.
        when(notifier.enabled()).thenReturn(true);
        DumConnectorIndexingStatsModel healthy = DumConnectorIndexingStatsModel.builder().build();
        DumConnectorIndexingStatsModel warned = DumConnectorIndexingStatsModel.builder()
                .warnings(new java.util.ArrayList<>(List.of("fell 92%"))).build();

        when(statsRepository.findAllBySourceAndProviderOrderByStartTimeDesc(eq("source-1"), eq("turing"),
                any(Limit.class))).thenReturn(List.of(healthy));
        warnedRun();
        verify(notifier).runStartedWarning(any(DumConnectorIndexingStatsModel.class));

        org.mockito.Mockito.clearInvocations(notifier);
        when(statsRepository.findAllBySourceAndProviderOrderByStartTimeDesc(eq("source-1"), eq("turing"),
                any(Limit.class))).thenReturn(List.of(warned));
        warnedRun();
        verify(notifier, never()).runStartedWarning(any(DumConnectorIndexingStatsModel.class));
    }

    private void warnedRun() {
        service.trackIndexingStart("source-1", "turing", OperationType.INDEX_ALL);
        service.recordRunOutcome("source-1", "turing", false, List.of(), List.of("fell 92%"));
        service.completeIndexingStats("source-1", "turing");
    }

    @Test
    void keepAliveStampsTheSessionsTransactionInChunks() {
        // D104: 1,200 unchanged ids reach the database as three bounded updates.
        DumConnectorSession session = new DumConnectorSession("source-1", "turing", Locale.US);
        List<String> ids = java.util.stream.IntStream.range(0, 1200).mapToObj(i -> "id-" + i).toList();
        when(repository.keepAlive(eq("source-1"), eq("turing"), eq(session.getTransactionId()), any()))
                .thenAnswer(inv -> inv.<Collection<String>>getArgument(3).size());

        assertThat(service.keepAlive(session, ids)).isEqualTo(1200);
        verify(repository, org.mockito.Mockito.times(3))
                .keepAlive(eq("source-1"), eq("turing"), eq(session.getTransactionId()), any());
        assertThat(service.keepAlive(session, List.of())).isZero();
    }

    @Test
    void anOutcomeForAnUntrackedRunIsDropped() {
        service.recordRunOutcome("source-1", "turing", true, List.of("interrupted"));

        assertThat(service.hasRunOutcome("source-1", "turing")).isFalse();
    }

    @Test
    void completeIndexingStatsWithoutPendingIsNoOp() {
        service.completeIndexingStats("nope", "turing");
        verify(statsRepository, never()).save(any());
    }

    @Test
    void saveCreatesNewWhenNoneExists() {
        DumJobItemWithSession jis = jobItem();
        when(repository.findByObjectIdAndSourceAndEnvironmentAndProvider(
                "obj-1", "source-1", "prod", "turing")).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.save(jis, DumIndexingStatus.INDEXED);

        verify(repository, org.mockito.Mockito.atLeastOnce())
                .save(any(DumConnectorIndexingModel.class));
    }

    @Test
    void saveUpdatesExisting() {
        DumJobItemWithSession jis = jobItem();
        DumConnectorIndexingModel existing = DumConnectorIndexingModel.builder()
                .objectId("obj-1").source("source-1").build();
        when(repository.findByObjectIdAndSourceAndEnvironmentAndProvider(
                "obj-1", "source-1", "prod", "turing")).thenReturn(List.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.save(jis, DumIndexingStatus.IGNORED);

        verify(repository, org.mockito.Mockito.atLeastOnce()).save(existing);
    }

    @Test
    void updateListSkipsEmptyAndSavesNonEmpty() {
        DumJobItemWithSession jis = jobItem();
        service.update(jis, List.of(), DumIndexingStatus.INDEXED);
        verify(repository, never()).saveAll(any());

        DumConnectorIndexingModel m = DumConnectorIndexingModel.builder().objectId("obj-1").build();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.update(jis, List.of(m), DumIndexingStatus.INDEXED);
        verify(repository).saveAll(any());
    }

    @Test
    void deindexedStatusAndUpdateDelegate() {
        DumJobItemWithSession jis = jobItem();
        when(repository.findByObjectIdAndSourceAndEnvironmentAndProvider(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.deindexedStatus(jis);
        service.update(jis);

        verify(repository, org.mockito.Mockito.atLeast(2)).save(any());
    }

    @Test
    void deleteDelegatesToRepository() {
        service.delete(jobItem());
        verify(repository).deleteByObjectIdAndSourceAndEnvironmentAndProvider(
                "obj-1", "source-1", "prod", "turing");
    }

    @Test
    void existsAndChecksumAndGetListDelegate() {
        DumJobItemWithSession jis = jobItem();
        when(repository.existsByObjectIdAndSourceAndEnvironmentAndProvider(
                "obj-1", "source-1", "prod", "turing")).thenReturn(true);
        assertThat(service.exists(jis)).isTrue();

        when(repository.existsByObjectIdAndSourceAndEnvironmentAndChecksumNot(
                eq("obj-1"), eq("source-1"), eq("prod"), any())).thenReturn(true);
        assertThat(service.isChecksumDifferent(jis)).isTrue();

        when(repository.findByObjectIdAndSourceAndEnvironmentAndProvider(
                "obj-1", "source-1", "prod", "turing")).thenReturn(List.of());
        assertThat(service.getList(jis)).isEmpty();
    }

    @Test
    void simpleDelegationsForwardToRepository() {
        service.deleteByProvider("turing");
        verify(repository).deleteByProvider("turing");

        service.findByDependencies("s", "p", List.of("r"));
        verify(repository).findObjectIdsByDependencies("s", "p", List.of("r"));

        service.findAllByProviderAndObjectIdIn("p", List.of("o"));
        verify(repository).findAllByProviderAndObjectIdIn("p", List.of("o"));

        service.deleteByProviderAndSourceAndObjectIdIn("p", "s", List.of("o"));
        verify(repository).deleteByProviderAndSourceAndObjectIdIn("p", "s", List.of("o"));

        service.deleteByProviderAndSource("p", "s");
        verify(repository).deleteByProviderAndSource("p", "s");

        service.deleteContentsToBeDeIndexed(new DumConnectorSession("s", "p", Locale.US));
        verify(repository).deleteBySourceAndProviderAndTransactionIdNot(eq("s"), eq("p"), any());

        service.getShouldBeDeIndexedList(new DumConnectorSession("s", "p", Locale.US));
        verify(repository).findBySourceAndProviderAndTransactionIdNotAndStandalone(
                eq("s"), eq("p"), any(), eq(false));

        service.findAll();
        verify(repository).findAllByOrderByModificationDateDesc(any(Limit.class));

        service.findById(7);
        verify(repository).findWithDependenciesById(7);

        service.getAllSources("p");
        verify(repository).findAllSources("p");

        service.getBySourceAndProvider("s", "p");
        verify(repository).findAllBySourceAndProviderOrderByModificationDateDesc(
                eq("s"), eq("p"), any(Limit.class));

        service.getSites("s", "p");
        verify(repository).distinctSites("s", "p");

        service.getEnvironment("s", "p");
        verify(repository).distinctEnvironment("s", "p");

        service.getUnprocessedBySourceAndProvider("s", "p");
        verify(repository).findAllBySourceAndProviderAndStatusOrderByModificationDateDesc(
                eq("s"), eq("p"), eq(DumIndexingStatus.NOT_PROCESSED), any(Limit.class));

        service.existsByObjectIdAndSourceAndProvider("o", "s", "p");
        verify(repository).existsByObjectIdAndSourceAndProvider("o", "s", "p");

        service.existsByObjectIdAndSourceAndEnvironmentAndProvider("o", "s", "e", "p");
        verify(repository).existsByObjectIdAndSourceAndEnvironmentAndProvider("o", "s", "e", "p");

        service.findUnprocessedByObjectIdAndSourceAndProvider("o", "s", "p");
        verify(repository).findByObjectIdAndSourceAndProviderAndStatus(
                "o", "s", "p", DumIndexingStatus.NOT_PROCESSED);

        service.deleteAll(List.of());
        verify(repository).deleteAll(List.of());

        service.countBySourceAndProviderSince("s", "p", Instant.EPOCH);
        verify(repository).countBySourceAndProviderAndModificationDateGreaterThanEqual(
                "s", "p", Instant.EPOCH);

        service.countByProvider("p");
        verify(repository).countByProvider("p");
    }

    @Test
    void siteLocaleScopedQueriesDelegate() {
        DumSNSiteLocale siteLocale = new DumSNSiteLocale();
        siteLocale.setLanguage(Locale.US);

        service.listIndexedObjectIds("s", "e", siteLocale, "p");
        verify(repository).findAllIndexedObjectIds("s", Locale.US, "e", "p");

        service.validateObjectIdList("s", "e", siteLocale, "p", List.of("o"));
        verify(repository).distinctObjectId("s", Locale.US, "e", "p", List.of("o"));
    }

    @Test
    void statsDelegations() {
        DumConnectorIndexingStatsModel stats = DumConnectorIndexingStatsModel.builder().build();
        when(statsRepository.save(stats)).thenReturn(stats);
        assertThat(service.saveStats(stats)).isSameAs(stats);

        service.getStatsBySourceAndProvider("s", "p");
        verify(statsRepository).findAllBySourceAndProviderOrderByStartTimeDesc(
                eq("s"), eq("p"), any(Limit.class));

        service.getAllStats();
        verify(statsRepository).findAllByOrderByStartTimeDesc(any(Limit.class));
    }

    @Test
    void countByProviderGroupBySourceMapsRows() {
        when(repository.countByProviderGroupBySource("p"))
                .thenReturn(List.of(new Object[] {"sourceA", 3L}, new Object[] {"sourceB", 7L}));

        assertThat(service.countByProviderGroupBySource("p"))
                .containsEntry("sourceA", 3L).containsEntry("sourceB", 7L);
    }

    @Test
    void getIndexingItemMapsModelsToDto() {
        DumConnectorIndexingModel model = DumConnectorIndexingModel.builder()
                .objectId("o").source("s").checksum("c").environment("e")
                .created(Instant.EPOCH).modificationDate(Instant.EPOCH)
                .status(DumIndexingStatus.INDEXED).build();
        when(repository.findByObjectIdAndSourceAndProvider("o", "s", "p"))
                .thenReturn(List.of(model));

        List<DumConnectorIndexing> dtos = service.getIndexingItem("o", "s", "p");

        assertThat(dtos).singleElement()
                .satisfies(dto -> assertThat(dto.getObjectId()).isEqualTo("o"));
    }

    @Test
    void createUnprocessedRecordOverloadsAllPersist() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.createUnprocessedRecord("o", "s", "p").getStatus())
                .isEqualTo(DumIndexingStatus.NOT_PROCESSED);
        assertThat(service.createUnprocessedRecord("o", "s", "p", Locale.US).getLocale())
                .isEqualTo(Locale.US);
        assertThat(service.createUnprocessedRecord("o", "s", "p", Locale.US, "e", List.of("site"))
                .getEnvironment()).isEqualTo("e");

        verify(repository, org.mockito.Mockito.times(3))
                .save(any(DumConnectorIndexingModel.class));
    }

    @Test
    void searchBuildsMonitoringPage() {
        DumConnectorMonitoringRequest request = DumConnectorMonitoringRequest.builder()
                .page(0).size(10).sortBy("objectId").sortDirection("asc").build();
        Page<DumConnectorIndexingModel> page = new PageImpl<>(List.of(
                DumConnectorIndexingModel.builder().objectId("o").build()));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(repository.findAllSources("turing")).thenReturn(List.of("s"));
        when(repository.findAllEnvironments("turing")).thenReturn(List.of("e"));
        when(repository.findAllLocales("turing")).thenReturn(List.of(Locale.US));
        when(repository.findAllSites("turing")).thenReturn(List.of("site"));

        DumConnectorMonitoringPage result = service.search(request, "turing");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getSources()).containsExactly("s");
        assertThat(result.getLocales()).containsExactly(Locale.US.toString());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchFallsBackToDefaultSortFieldForUnknownSortBy() {
        DumConnectorMonitoringRequest request = DumConnectorMonitoringRequest.builder()
                .sortBy("notAField").sortDirection("desc").build();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(repository.findAllLocales("turing")).thenReturn(List.of());

        DumConnectorMonitoringPage result = service.search(request, "turing");

        assertThat(result.getContent()).isEmpty();
    }
}
