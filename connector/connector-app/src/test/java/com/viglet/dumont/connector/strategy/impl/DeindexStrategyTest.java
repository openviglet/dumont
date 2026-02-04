/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.viglet.dumont.connector.batch.JobItemBatchProcessor;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumJobItemWithSession;
import com.viglet.dumont.connector.service.DumConnectorIndexingService;
import com.viglet.turing.client.sn.job.TurSNJobItem;

class DeindexStrategyTest {

    @Mock
    private DumConnectorIndexingService indexingService;

    @Mock
    private JobItemBatchProcessor batchProcessor;

    @Mock
    private TurSNJobItem jobItem;

    @Mock
    private DumConnectorSession session;

    private DeindexStrategy deindexStrategy;
    private DumJobItemWithSession jobItemWithSession;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        deindexStrategy = new DeindexStrategy(indexingService);
        jobItemWithSession = new DumJobItemWithSession(jobItem, session, new HashSet<>(), false);
    }

    @Test
    void testProcessCallsDeindexedStatus() {
        // Arrange
        when(jobItem.getId()).thenReturn("test-id");

        // Act
        deindexStrategy.process(jobItemWithSession, batchProcessor);

        // Assert
        verify(indexingService).deindexedStatus(jobItemWithSession);
    }

    @Test
    void testProcessCallsBatchProcessor() {
        // Arrange
        when(jobItem.getId()).thenReturn("test-id");

        // Act
        deindexStrategy.process(jobItemWithSession, batchProcessor);

        // Assert
        verify(batchProcessor).add(jobItem, session);
    }

    @Test
    void testGetPriority() {
        // Act
        int priority = deindexStrategy.getPriority();

        // Assert
        assertEquals(10, priority);
    }

    @Test
    void testProcessWithValidJobItem() {
        // Arrange
        when(jobItem.getId()).thenReturn("test-object-id");

        // Act & Assert
        assertDoesNotThrow(() -> {
            deindexStrategy.process(jobItemWithSession, batchProcessor);
        });
    }
}
