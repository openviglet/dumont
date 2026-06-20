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

package com.viglet.dumont.connector.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.strategy.JobProcessingStrategy;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;

class JobProcessingChainTest {

    private final JobItemBatchProcessor batchProcessor = mock(JobItemBatchProcessor.class);

    private static DumJobItemWithSession jobItem() {
        TurSNJobItem item = new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.US,
                Map.of("id", "c1"));
        return new DumJobItemWithSession(item, new DumConnectorSession("wknd", "turing", Locale.US),
                Set.of(), false);
    }

    private static JobProcessingStrategy strategy(int priority, boolean canHandle) {
        JobProcessingStrategy strategy = mock(JobProcessingStrategy.class);
        when(strategy.getPriority()).thenReturn(priority);
        lenient().when(strategy.canHandle(any())).thenReturn(canHandle);
        return strategy;
    }

    @Test
    void strategiesAreSortedByPriority() {
        JobProcessingStrategy low = strategy(10, false);
        JobProcessingStrategy high = strategy(40, false);

        JobProcessingChain chain = new JobProcessingChain(List.of(high, low));

        assertThat(chain.getStrategies()).containsExactly(low, high);
    }

    @Test
    void dispatchesToFirstHandlingStrategyInPriorityOrder() {
        JobProcessingStrategy first = strategy(10, true);
        JobProcessingStrategy second = strategy(20, true);
        JobProcessingChain chain = new JobProcessingChain(List.of(second, first));

        DumJobItemWithSession jobItem = jobItem();
        chain.process(jobItem, batchProcessor);

        verify(first).process(jobItem, batchProcessor);
        verify(second, never()).process(any(), any());
    }

    @Test
    void nullJobItemIsSkipped() {
        JobProcessingStrategy strategy = strategy(10, true);
        JobProcessingChain chain = new JobProcessingChain(List.of(strategy));

        chain.process(null, batchProcessor);

        verify(strategy, never()).process(any(), any());
    }

    @Test
    void noMatchingStrategyProcessesNothing() {
        JobProcessingStrategy strategy = strategy(10, false);
        JobProcessingChain chain = new JobProcessingChain(List.of(strategy));

        chain.process(jobItem(), batchProcessor);

        verify(strategy, never()).process(any(), any());
    }
}
