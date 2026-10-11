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

package com.viglet.dumont.connector.strategy.impl;

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.IGNORED;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_FORCED_REINDEX;
import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_REINDEX;
import static org.assertj.core.api.Assertions.assertThat;
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

import com.viglet.dumont.commons.indexing.DumIndexingStatus;
import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.persistence.model.DumConnectorIndexingModel;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

@ExtendWith(MockitoExtension.class)
class ReindexStrategyTest {

    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private JobItemBatchProcessor batchProcessor;

    private ReindexStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ReindexStrategy(indexingService);
    }

    private static DumJobItemWithSession jobItem(String checksum, boolean standalone) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("id", "c1");
        TurSNJobItem item = new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US,
                attrs);
        item.setChecksum(checksum);
        DumConnectorSession session = new DumConnectorSession("wknd", "turing", Locale.US);
        return new DumJobItemWithSession(item, session, Set.of(), standalone);
    }

    private static DumConnectorIndexingModel indexed(String checksum, DumIndexingStatus status) {
        return DumConnectorIndexingModel.builder().objectId("c1").checksum(checksum).status(status)
                .build();
    }

    @Test
    void priorityIsAfterIndexStrategy() {
        assertThat(strategy.getPriority()).isEqualTo(40);
    }

    @Test
    void standaloneHandlingDelegatesToExists() {
        DumJobItemWithSession jobItem = jobItem("v1", true);
        when(indexingService.exists(jobItem)).thenReturn(true);
        assertThat(strategy.canHandle(jobItem)).isTrue();
    }

    @Test
    void doesNotHandleWhenNoExistingRecords() {
        DumJobItemWithSession jobItem = jobItem("v1", false);
        when(indexingService.getList(jobItem)).thenReturn(List.of());
        assertThat(strategy.canHandle(jobItem)).isFalse();
    }

    @Test
    void handlesWhenChecksumChanged() {
        DumJobItemWithSession jobItem = jobItem("v2", false);
        when(indexingService.getList(jobItem)).thenReturn(List.of(indexed("v1", PREPARE_REINDEX)));
        assertThat(strategy.canHandle(jobItem)).isTrue();
    }

    @Test
    void doesNotHandleAnUnchangedChecksum() {
        DumJobItemWithSession jobItem = jobItem("v1", false);
        when(indexingService.getList(jobItem)).thenReturn(List.of(indexed("v1", PREPARE_REINDEX)));
        assertThat(strategy.canHandle(jobItem)).isFalse();
    }

    @Test
    void aMissingChecksumOnEitherSideReadsAsChangedInsteadOfThrowing() {
        DumJobItemWithSession noChecksum = jobItem(null, false);
        when(indexingService.getList(noChecksum)).thenReturn(List.of(indexed("v1", PREPARE_REINDEX)));
        assertThat(strategy.canHandle(noChecksum)).isTrue();

        DumJobItemWithSession againstNull = jobItem("v1", false);
        when(indexingService.getList(againstNull)).thenReturn(List.of(indexed(null, PREPARE_REINDEX)));
        assertThat(strategy.canHandle(againstNull)).isTrue();
    }

    @Test
    void handlesWhenPreviouslyIgnored() {
        DumJobItemWithSession jobItem = jobItem("v1", false);
        when(indexingService.getList(jobItem)).thenReturn(List.of(indexed("v1", IGNORED)));
        assertThat(strategy.canHandle(jobItem)).isTrue();
    }

    @Test
    void processUpdatesSingleRecord() {
        DumJobItemWithSession jobItem = jobItem("v2", false);
        List<DumConnectorIndexingModel> list = List.of(indexed("v1", PREPARE_REINDEX));
        when(indexingService.getList(jobItem)).thenReturn(list);

        strategy.process(jobItem, batchProcessor);

        verify(batchProcessor).add(jobItem);
        verify(indexingService).update(jobItem, list, PREPARE_REINDEX);
    }

    @Test
    void processRecreatesWhenDuplicatedRecords() {
        DumJobItemWithSession jobItem = jobItem("v2", false);
        when(indexingService.getList(jobItem))
                .thenReturn(List.of(indexed("v1", PREPARE_REINDEX), indexed("v1", PREPARE_REINDEX)));

        strategy.process(jobItem, batchProcessor);

        verify(indexingService).deindexedStatus(jobItem);
        verify(indexingService).save(jobItem, PREPARE_FORCED_REINDEX);
    }
}
