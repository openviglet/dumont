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

import static com.viglet.dumont.commons.indexing.DumIndexingStatus.PREPARE_INDEX;
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

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

@ExtendWith(MockitoExtension.class)
class IndexStrategyTest {

    @Mock
    private DumConnectorIndexingService indexingService;
    @Mock
    private JobItemBatchProcessor batchProcessor;

    private IndexStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new IndexStrategy(indexingService);
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
    void priorityIsBelowDeindexAndIgnore() {
        assertThat(strategy.getPriority()).isEqualTo(30);
    }

    @Test
    void handlesNewItemWithId() {
        DumJobItemWithSession jobItem = jobItem("c1");
        when(indexingService.exists(jobItem)).thenReturn(false);
        assertThat(strategy.canHandle(jobItem)).isTrue();
    }

    @Test
    void doesNotHandleItemWithoutId() {
        assertThat(strategy.canHandle(jobItem(null))).isFalse();
    }

    @Test
    void doesNotHandleAlreadyIndexedItem() {
        DumJobItemWithSession jobItem = jobItem("c1");
        when(indexingService.exists(jobItem)).thenReturn(true);
        assertThat(strategy.canHandle(jobItem)).isFalse();
    }

    @Test
    void processSavesPrepareIndexAndQueues() {
        DumJobItemWithSession jobItem = jobItem("c1");

        strategy.process(jobItem, batchProcessor);

        verify(indexingService).save(jobItem, PREPARE_INDEX);
        verify(batchProcessor).add(jobItem.turSNJobItem(), jobItem.session());
    }
}
