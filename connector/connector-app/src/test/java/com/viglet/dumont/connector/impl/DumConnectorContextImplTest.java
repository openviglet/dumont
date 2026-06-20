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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.chain.JobProcessingChain;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

@ExtendWith(MockitoExtension.class)
class DumConnectorContextImplTest {

    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private JobItemBatchProcessor batchProcessor;
    @Mock
    private JobProcessingChain processingChain;

    private DumConnectorContextImpl context;

    @BeforeEach
    void setUp() {
        context = new DumConnectorContextImpl(indexingService, batchProcessor, processingChain);
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

        verify(batchProcessor, times(1)).flush(session);
        verify(indexingService, never()).completeIndexingStats(any(), any());
        verify(indexingService).finishProcessing("wknd");
    }

    @Test
    void finishIndexingNonStandaloneDeindexesAndRecordsStats() {
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);
        when(indexingService.getShouldBeDeIndexedList(session)).thenReturn(List.of());

        context.finishIndexing(session, false);

        verify(batchProcessor, times(2)).flush(session);
        verify(indexingService).completeIndexingStats("wknd", "turing");
        verify(indexingService).finishProcessing("wknd");
    }

    @Test
    void getIndexingItemDelegates() {
        when(indexingService.getIndexingItem("o", "s", "p")).thenReturn(List.of());
        assertThat(context.getIndexingItem("o", "s", "p")).isEmpty();
    }
}
