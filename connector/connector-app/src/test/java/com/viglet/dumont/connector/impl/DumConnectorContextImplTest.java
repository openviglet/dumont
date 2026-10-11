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

package com.viglet.dumont.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.chain.JobProcessingChain;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumIndexingSchema;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.commons.plugin.DumIndexingPlugin;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;

@ExtendWith(MockitoExtension.class)
class DumConnectorContextImplTest {

    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private JobItemBatchProcessor batchProcessor;
    @Mock
    private JobProcessingChain processingChain;
    @Mock
    private DumIndexingPlugin indexingPlugin;

    private DumConnectorContextImpl context;

    @BeforeEach
    void setUp() {
        context = context(true);
    }

    private DumConnectorContextImpl context(boolean provisionEnabled) {
        return new DumConnectorContextImpl(indexingService, batchProcessor, processingChain,
                indexingPlugin, provisionEnabled);
    }

    private static DumIndexingSchema schema() {
        return new DumIndexingSchema("Sample", Locale.US,
                List.of(new TurSNJobAttributeSpec()));
    }

    @Test
    void provisionSchemaReachesTheIndexingPlugin() {
        DumIndexingSchema schema = schema();

        context.provisionSchema(schema);

        verify(indexingPlugin).provision(schema);
    }

    @Test
    void provisionSchemaIsSkippedWhenDisabled() {
        context(false).provisionSchema(schema());

        verify(indexingPlugin, never()).provision(any());
    }

    @Test
    void provisionSchemaIsSkippedForAnEmptyOrAbsentSchema() {
        context.provisionSchema(null);
        context.provisionSchema(new DumIndexingSchema("Sample", Locale.US, List.of()));

        verify(indexingPlugin, never()).provision(any());
    }

    private static DumJobItemWithSession jobItem(String id) {
        Map<String, Object> attrs = new HashMap<>();
        if (id != null) {
            attrs.put("id", id);
        }
        TurSNJobItem item = new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US,
                attrs);
        return new DumJobItemWithSession(item, new DumConnectorSession("wknd", "turing", Locale.US),
                Set.of(), false);
    }

    @Test
    void getObjectIdByDependencyReturnsEmptyForNoInput() {
        assertThat(context.getObjectIdByDependency("s", "p", null)).isEmpty();
        assertThat(context.getObjectIdByDependency("s", "p", List.of())).isEmpty();
        verify(indexingService, never()).findByDependencies(any(), any(), any());
    }

    @Test
    void getObjectIdByDependencyDeduplicates() {
        when(indexingService.findByDependencies("s", "p", List.of("d1")))
                .thenReturn(List.of("a", "a", "b"));
        assertThat(context.getObjectIdByDependency("s", "p", List.of("d1")))
                .containsExactly("a", "b");
    }

    @Test
    void addJobItemQueuesAndProcessesWhenIdPresent() {
        assertThat(context.addJobItem(jobItem("c1"))).isTrue();
        verify(processingChain).process(any(DumJobItemWithSession.class), any());
    }

    @Test
    void addJobItemReturnsFalseWhenIdMissing() {
        assertThat(context.addJobItem(jobItem(null))).isFalse();
        verify(processingChain, never()).process(any(), any());
    }

    @Test
    void addJobItemReturnsFalseWhenJobItemNull() {
        DumJobItemWithSession jobItem = new DumJobItemWithSession(null,
                new DumConnectorSession("wknd", "turing", Locale.US), Set.of(), false);
        assertThat(context.addJobItem(jobItem)).isFalse();
    }

    @Test
    void finishIndexingStandaloneFlushesOnceWithoutStats() {
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);

        context.finishIndexing(session, true);

        verify(batchProcessor, times(1)).flush();
        verify(indexingService, never()).completeIndexingStats(any(), any());
        verify(indexingService).finishProcessing("wknd");
    }

    @Test
    void aStandaloneFinishWithARecordedOutcomeStillWritesTheRunsStats() {
        // D96: a tracked run that stopped short gets a stats row saying so, without the sweep.
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);
        when(indexingService.hasRunOutcome("wknd", "turing")).thenReturn(true);

        context.recordRunOutcome(session, true, List.of("stopped at the call cap of 20 per run"));
        context.finishIndexing(session, true);

        verify(indexingService).recordRunOutcome("wknd", "turing", true,
                List.of("stopped at the call cap of 20 per run"));
        verify(indexingService).completeIndexingStats("wknd", "turing");
        verify(indexingService, never()).getShouldBeDeIndexedList(any());
    }

    @Test
    void finishIndexingNonStandaloneDeindexesAndRecordsStats() {
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);
        when(indexingService.getShouldBeDeIndexedList(session)).thenReturn(List.of());

        context.finishIndexing(session, false);

        verify(batchProcessor, times(2)).flush();
        verify(indexingService).completeIndexingStats("wknd", "turing");
        verify(indexingService).finishProcessing("wknd");
    }

    @Test
    void finishIndexingNonStandaloneCreatesDeleteJobsForDeindexedItems() {
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);
        DumConnectorIndexingModel stale = org.mockito.Mockito.mock(DumConnectorIndexingModel.class);
        when(stale.getObjectId()).thenReturn("/content/gone");
        when(stale.getSites()).thenReturn(List.of("Sample"));
        when(stale.getLocale()).thenReturn(Locale.US);
        when(indexingService.getShouldBeDeIndexedList(session)).thenReturn(List.of(stale));

        context.finishIndexing(session, false);

        // D69: the delete job carries the session that queued it, so a drain another source asks
        // for still files this row under 'wknd'.
        ArgumentCaptor<DumJobItemWithSession> deferred =
                ArgumentCaptor.forClass(DumJobItemWithSession.class);
        verify(batchProcessor).addDeferred(deferred.capture());
        assertThat(deferred.getValue().session()).isSameAs(session);
        assertThat(deferred.getValue().turSNJobItem().getId()).isEqualTo("/content/gone");
        verify(indexingService).deleteContentsToBeDeIndexed(session);
    }

    private static DumConnectorIndexingModel row(String source, Locale locale, String... sites) {
        return DumConnectorIndexingModel.builder().objectId("job-1").provider("turing").source(source)
                .locale(locale).sites(new java.util.ArrayList<>(List.of(sites))).build();
    }

    private List<DumJobItemWithSession> sweep(DumConnectorSession session, DumConnectorIndexingModel stale,
            List<DumConnectorIndexingModel> others) {
        when(indexingService.getShouldBeDeIndexedList(session)).thenReturn(List.of(stale));
        when(indexingService.findAllByProviderAndObjectIdIn("turing", List.of("job-1"))).thenReturn(others);

        context.finishIndexing(session, false);

        ArgumentCaptor<DumJobItemWithSession> deferred = ArgumentCaptor.forClass(DumJobItemWithSession.class);
        verify(batchProcessor, org.mockito.Mockito.atLeast(0)).addDeferred(deferred.capture());
        verify(indexingService).deleteContentsToBeDeIndexed(session);
        return deferred.getAllValues();
    }

    @Test
    void aDocumentAnotherSourceStillTracksIsNotDeleted() {
        // D111: adzuna stopped publishing job-1, jooble still tracks it on the same site and locale.
        DumConnectorSession session = new DumConnectorSession("adzuna", "turing", Locale.US);

        List<DumJobItemWithSession> deletes = sweep(session, row("adzuna", Locale.US, "Sample"),
                List.of(row("adzuna", Locale.US, "Sample"), row("jooble", Locale.US, "Sample")));

        assertThat(deletes).isEmpty();
    }

    @Test
    void onlyTheSitesNoOtherSourceTracksAreDeleted() {
        DumConnectorSession session = new DumConnectorSession("adzuna", "turing", Locale.US);

        List<DumJobItemWithSession> deletes = sweep(session, row("adzuna", Locale.US, "Sample", "Other"),
                List.of(row("jooble", Locale.US, "Sample")));

        assertThat(deletes).hasSize(1);
        assertThat(deletes.get(0).turSNJobItem().getSiteNames()).containsExactly("Other");
    }

    @Test
    void anotherSourceInAnotherLocaleDoesNotKeepTheDocument() {
        DumConnectorSession session = new DumConnectorSession("adzuna", "turing", Locale.US);

        List<DumJobItemWithSession> deletes = sweep(session, row("adzuna", Locale.US, "Sample"),
                List.of(row("jooble", Locale.of("pt", "BR"), "Sample")));

        assertThat(deletes).hasSize(1);
        assertThat(deletes.get(0).turSNJobItem().getSiteNames()).containsExactly("Sample");
    }

    @Test
    void getIndexingItemDelegates() {
        when(indexingService.getIndexingItem("o", "s", "p")).thenReturn(List.of());
        assertThat(context.getIndexingItem("o", "s", "p")).isEmpty();
    }
}
